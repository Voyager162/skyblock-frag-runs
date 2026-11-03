package dev.fragruns.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Public, keyless Hypixel endpoints only. Network and cache writes never run on the render thread. */
public final class MarketService implements AutoCloseable {
    public record Prices(long updatedAt, Map<String, Double> values) {}
    private volatile Prices prices = new Prices(0, Map.of());
    private volatile String failure = "";
    private volatile long nextRefresh;
    private long nextCatalog;
    private final AtomicBoolean busy = new AtomicBoolean();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r,"FragRuns prices"); t.setDaemon(true); return t; });
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private final Path cache;
    private final ItemCatalog catalog;

    public MarketService(Path directory, ItemCatalog catalog) {
        this.cache = directory.resolve("prices.json"); this.catalog = catalog;
        if (Files.isRegularFile(cache)) try {
            Prices saved = JsonFiles.read(cache,Prices.class);
            if (saved.values != null && saved.updatedAt > 0) prices = new Prices(saved.updatedAt, sanitize(saved.values));
        } catch (IOException ignored) { failure = "Cached prices unavailable"; }
    }
    public Prices prices() { return prices; }
    public String failure() { return failure; }
    public boolean busy() { return busy.get(); }
    public long ageSeconds() { return prices.updatedAt == 0 ? -1 : Math.max(0,(System.currentTimeMillis()-prices.updatedAt)/1000); }
    public boolean stale() { return ageSeconds() < 0 || ageSeconds() > 180; }
    public void tick(int intervalSeconds) {
        long now = System.currentTimeMillis();
        if (now < nextRefresh || !busy.compareAndSet(false,true)) return;
        nextRefresh = now + Math.max(30,intervalSeconds)*1000L;
        worker.execute(() -> {
            try {
                Prices fresh = parseBazaar(fetch("https://api.hypixel.net/v2/skyblock/bazaar"));
                prices = fresh;
                failure = "";
                try { JsonFiles.write(cache, fresh); } catch (IOException ignored) { /* Live prices remain valid. */ }
            } catch (Exception e) { failure = "Price update failed; last good prices retained"; }
            try {
                if (now >= nextCatalog) {
                    nextCatalog = now + 6*60*60*1000L;
                    catalog.replace(fetch("https://api.hypixel.net/v2/resources/skyblock/items"));
                }
            } catch (Exception ignored) { /* The bundled catalog remains usable offline. */ }
            finally { busy.set(false); }
        });
    }
    private JsonObject fetch(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(12))
                .header("User-Agent","SkyblockFragRuns/1.1.0 (client-side dungeon tracker)").GET().build();
        var response = client.send(request,HttpResponse.BodyHandlers.ofString());
        if (response.statusCode()!=200) throw new IOException("HTTP "+response.statusCode());
        if (response.body().length()>15_000_000) throw new IOException("Response too large");
        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        if (!json.has("success") || !json.get("success").getAsBoolean()) throw new IOException("API unavailable");
        return json;
    }
    public static Prices parseBazaar(JsonObject root) {
        if (!root.has("success") || !root.get("success").getAsBoolean() || !root.has("products"))
            throw new IllegalArgumentException("Invalid Bazaar response");
        Map<String,Double> result = new HashMap<>();
        for (var entry : root.getAsJsonObject("products").entrySet()) {
            var product = entry.getValue().getAsJsonObject();
            var orders = product.getAsJsonArray("sell_summary");
            // sell_summary contains the standing buy orders used when the player instant-sells.
            if (orders != null && !orders.isEmpty()) {
                double price = orders.get(0).getAsJsonObject().get("pricePerUnit").getAsDouble();
                if (Double.isFinite(price) && price > 0) result.put(entry.getKey(),price);
            }
        }
        if (result.isEmpty()) throw new IllegalArgumentException("No tradable Bazaar products");
        long timestamp = root.has("lastUpdated") ? root.get("lastUpdated").getAsLong() : System.currentTimeMillis();
        if (timestamp <= 0 || timestamp > System.currentTimeMillis()+60_000) throw new IllegalArgumentException("Invalid price timestamp");
        return new Prices(timestamp,Map.copyOf(result));
    }
    private static Map<String,Double> sanitize(Map<String,Double> input) {
        Map<String,Double> safe = new HashMap<>();
        input.forEach((id,price)->{ if(id!=null && price!=null && Double.isFinite(price) && price>0) safe.put(id,price); });
        return Map.copyOf(safe);
    }
    @Override public void close() { worker.shutdownNow(); client.close(); }
}
