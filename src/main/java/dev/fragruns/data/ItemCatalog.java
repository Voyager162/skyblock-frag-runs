package dev.fragruns.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public final class ItemCatalog {
    public record Item(String id, String name, String tier, double npcPrice, boolean raritySalvageable,
                       String essenceId, int fixedSalvage) {
        public int salvageAmount(String actualTier, int quality) {
            return salvageAmount(actualTier,quality,0);
        }
        public int salvageAmount(String actualTier,int quality,int stars) {
            if (fixedSalvage <= 0 || essenceId == null) return 0;
            // Base yield belongs to the item family, not the rolled rarity printed in its lore.
            return fixedSalvage + (quality >= 50 ? 10 : 0) + Math.clamp(stars,0,10)*2;
        }
    }
    private volatile Map<String, Item> items = Map.of();
    public ItemCatalog() {
        try (var stream = ItemCatalog.class.getResourceAsStream("/data/fragruns/items.json")) {
            if (stream != null) replace(JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject());
        } catch (Exception e) { throw new IllegalStateException("Bundled item catalog cannot be read", e); }
    }
    public Item get(String id) { return items.get(id); }
    public int size() { return items.size(); }
    public void replace(JsonObject root) {
        JsonArray list = root.getAsJsonArray("items");
        if (list == null || list.size() == 0) throw new IllegalArgumentException("Empty item catalog");
        Map<String, Item> next = new HashMap<>();
        for (JsonElement element : list) {
            JsonObject obj = element.getAsJsonObject();
            String id = string(obj,"id","");
            if (id.isEmpty()) continue;
            String essence = null; int fixed = 0;
            if (obj.has("salvages")) {
                for (JsonElement entry : obj.getAsJsonArray("salvages")) {
                    JsonObject cost = entry.getAsJsonObject();
                    if ("ESSENCE".equals(string(cost,"type",""))) {
                        essence = "ESSENCE_" + cost.get("essence_type").getAsString();
                        fixed = cost.get("amount").getAsInt();
                        break;
                    }
                }
            }
            if (essence == null && obj.has("upgrade_costs") && !obj.getAsJsonArray("upgrade_costs").isEmpty()) {
                for (JsonElement entry : obj.getAsJsonArray("upgrade_costs").get(0).getAsJsonArray()) {
                    JsonObject cost = entry.getAsJsonObject();
                    if ("ESSENCE".equals(string(cost,"type",""))) { essence = "ESSENCE_" + cost.get("essence_type").getAsString(); break; }
                }
            }
            double npc = obj.has("npc_sell_price") ? obj.get("npc_sell_price").getAsDouble() : 0;
            if(fixed==0 && obj.has("rarity_salvageable") && obj.get("rarity_salvageable").getAsBoolean()) fixed=dungeonSalvageBase(id);
            if (!Double.isFinite(npc) || npc < 0) npc = 0;
            next.put(id, new Item(id,string(obj,"name",id),string(obj,"tier","COMMON"),npc,
                    obj.has("rarity_salvageable") && obj.get("rarity_salvageable").getAsBoolean(), essence, Math.max(0,fixed)));
        }
        items = Map.copyOf(next);
    }
    private static String string(JsonObject obj, String key, String fallback) { return obj.has(key) ? obj.get(key).getAsString() : fallback; }
    private static int dungeonSalvageBase(String id) {
        // Public Hypixel salvage table. Unknown families remain unestimated instead of borrowing a rarity's yield.
        if(id.equals("EARTH_SHARD") || id.equals("FEL_SWORD") || id.equals("ZOMBIE_COMMANDER_WHIP") || id.equals("ZOMBIE_KNIGHT_SWORD")) return 15;
        for(String family:java.util.List.of("SKELETON_GRUNT_","SKELETON_SOLDIER_","SKELETON_MASTER_","SKELETON_LORD_","SKELETOR_","SUPER_HEAVY_","ZOMBIE_COMMANDER_","ZOMBIE_KNIGHT_","ZOMBIE_LORD_","ZOMBIE_SOLDIER_"))
            if(id.startsWith(family) && (id.endsWith("HELMET")||id.endsWith("CHESTPLATE")||id.endsWith("LEGGINGS")||id.endsWith("BOOTS"))) return 15;
        for(String family:java.util.List.of("ROTTEN_","HEAVY_","BOUNCY_")) if(id.startsWith(family)) return 5;
        return switch(id) {
            case "CONJURING_SWORD","SILENT_DEATH","CRYPT_DREADLORD_SWORD","MACHINE_GUN_BOW","ZOMBIE_SOLDIER_CUTLASS","SNIPER_BOW","SNIPER_HELMET","CRYPT_BOW" -> 5;
            default -> 0;
        };
    }
}
