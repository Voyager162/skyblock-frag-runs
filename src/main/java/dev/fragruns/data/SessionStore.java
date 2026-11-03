package dev.fragruns.data;

import dev.fragruns.core.ModConfig;
import dev.fragruns.core.Session;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.LinkOption;
import java.util.regex.Pattern;

public final class SessionStore {
    private final Path directory;
    private String warning = "";
    public SessionStore(Path directory) { this.directory = directory; }
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
        if (!session.id().isBlank()) save(directory.resolve("history").resolve(session.id().replaceAll("[^a-zA-Z0-9._-]", "_")+".json"), session.snapshot());
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
