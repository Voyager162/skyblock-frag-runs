package dev.fragruns.data;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Set;
import java.util.UUID;

/** Store one independent room layout per run. A late write cannot resurrect a cleared archive. */
public final class RunMaps {
    private final Path directory;
    private long generation;
    public RunMaps(Path directory) { this.directory=directory; }
    public synchronized long generation() { return generation; }
    private void checkFolder() throws IOException {
        if (Files.exists(directory,LinkOption.NOFOLLOW_LINKS) && !Files.isDirectory(directory,LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Unexpected map folder");
    }
    public DungeonLayout read(UUID run) throws IOException {
        checkFolder();
        Path file=directory.resolve(run+".json");
        if (!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)) throw new NoSuchFileException(file.toString());
        if (Files.size(file)>262_144) throw new IOException("Map exceeds size limit");
        return JsonFiles.read(file,DungeonLayout.class);
    }
    public boolean hasLegacyImage(UUID run) {
        return Files.isDirectory(directory,LinkOption.NOFOLLOW_LINKS)
                && Files.isRegularFile(directory.resolve(run+".png"),LinkOption.NOFOLLOW_LINKS);
    }
    public void save(UUID run,long expected,DungeonLayout layout) throws IOException {
        checkFolder(); Files.createDirectories(directory);
        Path temp=Files.createTempFile(directory,"layout-",".tmp");
        try {
            Files.writeString(temp,JsonFiles.GSON.toJson(layout),StandardCharsets.UTF_8);
            synchronized(this) {
                if (generation!=expected) return;
                Path dest=directory.resolve(run+".json");
                try { Files.move(temp,dest,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE); }
                catch (AtomicMoveNotSupportedException e) { Files.move(temp,dest,StandardCopyOption.REPLACE_EXISTING); }
            }
        } finally { Files.deleteIfExists(temp); }
    }
    public synchronized void clearExcept(Set<UUID> keep) throws IOException {
        generation++; checkFolder();
        if (!Files.isDirectory(directory)) return;
        try (var paths=Files.newDirectoryStream(directory)) {
            for (Path file:paths) {
                String name=file.getFileName().toString();
                if (!name.endsWith(".json") && !name.endsWith(".png")) continue;
                String stem=name.substring(0,name.lastIndexOf('.'));
                UUID id;
                try { id=UUID.fromString(stem); } catch (IllegalArgumentException e) { continue; }
                if (stem.equals(id.toString()) && !keep.contains(id) && Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))
                    Files.deleteIfExists(file);
            }
        }
    }
}
