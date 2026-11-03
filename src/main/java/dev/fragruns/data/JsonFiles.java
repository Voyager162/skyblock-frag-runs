package dev.fragruns.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class JsonFiles {
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private JsonFiles() {}
    public static <T> T read(Path path, Class<T> type) throws IOException {
        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            T value = GSON.fromJson(reader, type);
            if (value == null) throw new IOException("Empty JSON file: " + path.getFileName());
            return value;
        } catch (RuntimeException e) { throw new IOException("Invalid JSON: " + path.getFileName(), e); }
    }
    public static void write(Path path, Object value) throws IOException {
        Files.createDirectories(path.getParent());
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(temporary, GSON.toJson(value), StandardCharsets.UTF_8);
        if (Files.isRegularFile(path)) Files.copy(path, path.resolveSibling(path.getFileName() + ".bak"), StandardCopyOption.REPLACE_EXISTING);
        try { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException e) { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING); }
    }
}
