package dev.fragruns.data;

import dev.fragruns.core.ModConfig;
import dev.fragruns.core.Session;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.LinkOption;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;

public final class SessionStore {
    private final Path directory;
    private final RunMaps maps;
    private String warning = "";
    public SessionStore(Path directory) { this.directory = directory; maps = new RunMaps(directory.resolve("maps")); }
    public RunMaps maps() { return maps; }
    public Path directory() { return directory; }
    public String warning() { return warning; }
    public ModConfig loadConfig() {
        Path path = directory.resolve("settings.json");
        if (Files.exists(path)) {
            try { ModConfig c = JsonFiles.read(path, ModConfig.class); c.sanitize(); return c; }
            catch (IOException e) { preserve(path); warning = "Settings recovered to defaults; original file preserved."; }
        }
        return new ModConfig();
    }
    public void loadSession(Session session) {
        Path path = directory.resolve("session.json");
        if (!Files.exists(path)) return;
        try { session.restore(JsonFiles.read(path, Session.Snapshot.class)); }
        catch (IOException | IllegalArgumentException e) {
            preserve(path);
            try {
                session.restore(JsonFiles.read(directory.resolve("session.json.bak"), Session.Snapshot.class));
                warning = "Session recovered from backup; damaged save preserved.";
            } catch (IOException | IllegalArgumentException fallback) {
                warning = "Could not restore the last session; damaged save preserved.";
            }
        }
    }
    private void preserve(Path path) {
        try { Files.copy(path, path.resolveSibling(path.getFileName()+".corrupt-"+System.currentTimeMillis())); }
        catch (IOException e) { warning = "Could not preserve damaged save: " + e.getMessage(); }
    }
    public void saveConfig(ModConfig config) { config.sanitize(); save(directory.resolve("settings.json"), config); }
    public void saveSession(Session session) {
        if (!session.id().isBlank()) save(directory.resolve("session.json"), session.snapshot());
    }
    public void archive(Session session) {
        try { archiveChecked(session); }
        catch (IOException e) { warning = "Could not archive session: " + e.getMessage(); }
    }
    public void archiveChecked(Session session) throws IOException {
        if (!session.id().isBlank()) JsonFiles.write(archivePath(session.id()), session.snapshot());
    }
    private Path archivePath(String id) throws IOException {
        if (id == null || !id.matches("[a-zA-Z0-9._-]{1,128}")) throw new IOException("Invalid session ID");
        Path history = directory.resolve("history");
        if (Files.exists(history,LinkOption.NOFOLLOW_LINKS) && !Files.isDirectory(history,LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Unexpected history folder");
        return history.resolve(id+".json");
    }
    public record Summary(String id,long elapsedMs,int runs) {
        public static Summary of(Session s) { return new Summary(s.id(),s.elapsedMs(),s.totalRuns()); }
        public Instant started() {
            try { return Instant.parse(id.replaceFirst("T(\\d{2})-(\\d{2})-(\\d{2})","T$1:$2:$3")); }
            catch (DateTimeParseException e) { return Instant.EPOCH; }
        }
    }
    public record Catalog(List<Summary> sessions,int unreadable) {}
    public Catalog catalog() throws IOException {
        Path history = directory.resolve("history");
        if (!Files.exists(history,LinkOption.NOFOLLOW_LINKS)) return new Catalog(List.of(),0);
        if (!Files.isDirectory(history,LinkOption.NOFOLLOW_LINKS)) throw new IOException("Unexpected history folder");
        var sessions = new ArrayList<Summary>();
        int unreadable = 0;
        try (var paths = Files.newDirectoryStream(history,"*.json")) {
            for (Path file : paths) {
                if (!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)) continue;
                String name = file.getFileName().toString();
                try { sessions.add(Summary.of(readArchived(name.substring(0,name.length()-5)))); }
                catch (IOException e) { unreadable++; }
            }
        }
        sessions.sort(Comparator.comparing(Summary::started).reversed().thenComparing(Summary::id));
        return new Catalog(List.copyOf(sessions),unreadable);
    }
    public Session readArchived(String id) throws IOException {
        Path file = archivePath(id);
        try { return readArchive(file,id); }
        catch (IOException primary) {
            if (!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)) throw primary;
            return readArchive(file.resolveSibling(file.getFileName()+".bak"),id);
        }
    }
    private Session readArchive(Path file,String id) throws IOException {
        if (!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)) throw new IOException("Session unavailable");
        var loaded = Session.live();
        try { loaded.restore(JsonFiles.read(file,Session.Snapshot.class)); }
        catch (IllegalArgumentException e) { throw new IOException("Invalid session",e); }
        if (!id.equals(loaded.id())) throw new IOException("Session ID mismatch");
        return loaded;
    }
    /** Commit the outgoing archive and incoming checkpoint before replacing any live state. */
    public void continueSession(Session live,String id) throws IOException {
        if (live.id().equals(id)) {
            if (id.isBlank()) throw new IOException("No session selected");
            if (!live.running()) {
                JsonFiles.write(directory.resolve("session.json"),live.snapshot());
                live.resumeSaved();
            }
            return;
        }
        var selected = readArchived(id).snapshot();
        if (!live.id().isBlank()) JsonFiles.write(archivePath(live.id()),live.switchingAway());
        JsonFiles.write(directory.resolve("session.json"),selected);
        live.restore(selected);
        live.resumeSaved();
    }
    private static final Pattern ARCHIVE_FILE = Pattern.compile(".+\\.json(?:\\.(?:bak|tmp|corrupt-\\d+))?");
    private static final Pattern CHECKPOINT = Pattern.compile("session\\.json(?:\\.(?:bak|tmp|corrupt-\\d+))?");

    /** Clear this profile's past sessions. A live session keeps its in-memory state and a current checkpoint. */
    public void clearHistory(Session session) throws IOException {
        Path history = directory.resolve("history");
        if (Files.exists(history, LinkOption.NOFOLLOW_LINKS)) {
            if (!Files.isDirectory(history, LinkOption.NOFOLLOW_LINKS)
                    || !history.toRealPath().startsWith(directory.toRealPath())) {
                throw new IOException("Unexpected history folder");
            }
            deleteSessionFiles(history, ARCHIVE_FILE, false);
        }
        if (session.active()) JsonFiles.write(directory.resolve("session.json"), session.snapshot());
        var keep = new HashSet<java.util.UUID>();
        if (session.active()) {
            for (var r : session.runs()) if (r.details()!=null) keep.add(r.details().id());
            if (session.currentRunId()!=null) keep.add(session.currentRunId());
        }
        maps.clearExcept(keep);
        if (Files.isDirectory(directory)) deleteSessionFiles(directory, CHECKPOINT, true);
        if (!session.active()) {
            // Last: a failed operation remains visible and can be retried.
            Files.deleteIfExists(directory.resolve("session.json"));
            session.clearSaved();
        }
    }

    private static void deleteSessionFiles(Path folder, Pattern names, boolean keepCheckpoint) throws IOException {
        try (var paths = Files.newDirectoryStream(folder)) {
            for (Path path : paths) {
                String name = path.getFileName().toString();
                if (keepCheckpoint && name.equals("session.json")) continue;
                if (names.matcher(name).matches() && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }
    private void save(Path file, Object value) {
        try { JsonFiles.write(file, value); }
        catch (IOException e) { warning = "Save failed: " + e.getMessage(); }
    }
}
