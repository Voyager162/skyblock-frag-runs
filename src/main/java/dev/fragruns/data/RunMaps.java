package dev.fragruns.data;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.Set;
import java.util.UUID;
import javax.imageio.ImageIO;

/** One replaceable PNG per run. An encoder finishing after Clear cannot resurrect a deleted map. */
public final class RunMaps {
    public static final int MAX_SIZE = 512;
    private final Path directory;
    private long generation;

    public RunMaps(Path directory) { this.directory = directory; }
    public synchronized long generation() { return generation; }
    private void checkFolder() throws IOException {
        if (Files.exists(directory,LinkOption.NOFOLLOW_LINKS) && !Files.isDirectory(directory,LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Unexpected map folder");
    }
    public InputStream open(UUID run) throws IOException {
        checkFolder();
        Path path = directory.resolve(run+".png");
        if (!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)) throw new NoSuchFileException(path.toString());
        return Files.newInputStream(path,LinkOption.NOFOLLOW_LINKS);
    }
    public void save(UUID run,long expected,int[] argb,int width,int height) throws IOException {
        if (width <= 0 || height <= 0 || width > MAX_SIZE || height > MAX_SIZE || argb.length != width*height)
            throw new IOException("Invalid map dimensions");
        checkFolder();
        Files.createDirectories(directory);
        var image = new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0,0,width,height,argb,0,width);
        Path temporary = Files.createTempFile(directory,"capture-",".tmp");
        try {
            try (var output = Files.newOutputStream(temporary)) {
                if (!ImageIO.write(image,"png",output)) throw new IOException("PNG encoder unavailable");
            }
            synchronized (this) {
                if (expected != generation) return;
                Path dest = directory.resolve(run+".png");
                try { Files.move(temporary,dest,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE); }
                catch (AtomicMoveNotSupportedException e) { Files.move(temporary,dest,StandardCopyOption.REPLACE_EXISTING); }
            }
        } finally { Files.deleteIfExists(temporary); }
    }
    public synchronized void clearExcept(Set<UUID> keep) throws IOException {
        generation++;
        checkFolder();
        if (!Files.isDirectory(directory)) return;
        try (var paths = Files.newDirectoryStream(directory,"*.png")) {
            for (Path p : paths) {
                String name = p.getFileName().toString();
                UUID id;
                try { id = UUID.fromString(name.substring(0,name.length()-4)); }
                catch (IllegalArgumentException e) { continue; }
                if (name.equals(id+".png") && !keep.contains(id) && Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS))
                    Files.deleteIfExists(p);
            }
        }
    }
}
