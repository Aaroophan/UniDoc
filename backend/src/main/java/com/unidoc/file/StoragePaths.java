package com.unidoc.file;

import com.unidoc.config.StorageProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

@Component
public class StoragePaths {

    private static final String REQUESTS_DIRECTORY = "requests";

    private final StorageProperties properties;
    private Path root;

    public StoragePaths(StorageProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void initialize() {
        root = properties.root().toAbsolutePath().normalize();
        rejectPublicApplicationDirectory(root);
        createWritableDirectory(root, "storage root");
        createWritableDirectory(root.resolve(".tmp"), "storage temporary directory");
    }

    public Path temporaryDirectory() {
        return root.resolve(".tmp");
    }

    public Path supportingDirectory(long requestId) {
        return requestDirectory(requestId, "supporting");
    }

    public Path additionalInformationDirectory(long requestId) {
        return requestDirectory(requestId, "additional-information");
    }

    public Path issuedDirectory(long requestId) {
        return requestDirectory(requestId, "issued");
    }

    public String toDatabaseRelativePath(Path storedFile) {
        Path absoluteFile = storedFile.isAbsolute()
                ? storedFile.normalize()
                : root.resolve(storedFile).normalize();

        if (!absoluteFile.startsWith(root)) {
            throw new IllegalArgumentException("Storage path must remain below the configured root.");
        }

        return root.relativize(absoluteFile).toString().replace('\\', '/');
    }

    private Path requestDirectory(long requestId, String category) {
        if (requestId < 1) {
            throw new IllegalArgumentException("Request ID must be positive.");
        }

        Path directory = root
                .resolve(REQUESTS_DIRECTORY)
                .resolve(Long.toString(requestId))
                .resolve(category)
                .normalize();

        if (!directory.startsWith(root)) {
            throw new IllegalStateException("Resolved request storage path escaped the configured root.");
        }

        return directory;
    }

    private static void createWritableDirectory(Path directory, String description) {
        if (Files.exists(directory) && !Files.isDirectory(directory)) {
            throw new IllegalStateException("The configured " + description + " is not a directory.");
        }

        try {
            Files.createDirectories(directory);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to create the configured " + description + ".", exception);
        }

        if (!Files.isDirectory(directory)) {
            throw new IllegalStateException("The configured " + description + " is not a directory.");
        }

        if (!Files.isWritable(directory)) {
            throw new IllegalStateException("The configured " + description + " is not writable.");
        }
    }

    private static void rejectPublicApplicationDirectory(Path candidate) {
        String path = candidate.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
        if (path.contains("/frontend/public")
                || path.contains("/backend/src/main/resources/static")) {
            throw new IllegalStateException("The storage root cannot be inside a public application directory.");
        }
    }
}
