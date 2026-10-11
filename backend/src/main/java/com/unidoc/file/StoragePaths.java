package com.unidoc.file;

import com.unidoc.config.StorageProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The filesystem boundary. Clients never supply paths: only metadata read from an
 * already-authorized request/attachment/issued-document record reaches resolveStoredFile.
 */
@Component
public class StoragePaths {

    private static final String REQUESTS_DIRECTORY = "requests";
    private static final Pattern STORED_RELATIVE_PATH = Pattern.compile(
            "requests/[1-9][0-9]*/(supporting|additional-information|issued)/"
                    + "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(pdf|jpg|png)"
    );

    private final StorageProperties properties;
    private Path root;

    public StoragePaths(StorageProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void initialize() {
        if (properties.root() == null) {
            throw new IllegalStateException("The storage root must be configured.");
        }
        if (properties.supportingMaxBytes() <= 0 || properties.issuedMaxBytes() <= 0
                || properties.supportingMaxBytes() > 10 * 1024 * 1024
                || properties.issuedMaxBytes() > 10 * 1024 * 1024) {
            throw new IllegalStateException("The file size limits must be positive and at most 10 MiB.");
        }

        Path configured = resolveConfiguredRoot(properties.root());
        rejectPublicApplicationDirectory(configured);
        createWritableDirectory(configured, "storage root");
        try {
            root = configured.toRealPath();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to resolve the configured storage root.", exception);
        }
        rejectPublicApplicationDirectory(root);
        createWritableDirectory(root.resolve(".tmp"), "storage temporary directory");
    }

    public Path storageRoot() {
        return initializedRoot();
    }

    public Path temporaryDirectory() {
        return initializedRoot().resolve(".tmp");
    }

    /** Fail closed if a local filesystem change replaced the private staging directory. */
    void verifyTemporaryDirectory() throws IOException {
        Path rootPath = initializedRoot();
        Path temporary = temporaryDirectory();
        if (Files.isSymbolicLink(rootPath) || Files.isSymbolicLink(temporary)
                || !Files.isDirectory(rootPath, LinkOption.NOFOLLOW_LINKS)
                || !Files.isDirectory(temporary, LinkOption.NOFOLLOW_LINKS)
                || !rootPath.toRealPath().equals(rootPath)
                || !temporary.toRealPath().equals(temporary)) {
            throw new IOException("The temporary storage directory is unsafe.");
        }
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

    /** Creates only the expected request subdirectories, rejecting symlinked ancestors. */
    void ensureRequestDirectory(Path directory) throws IOException {
        Path current = initializedRoot();
        if (Files.isSymbolicLink(current) || !current.toRealPath().equals(current)) {
            throw new IOException("Unsafe storage root.");
        }
        if (!directory.normalize().startsWith(current)) {
            throw new IOException("Unsafe storage directory.");
        }
        for (Path segment : current.relativize(directory.normalize())) {
            current = current.resolve(segment);
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                if (Files.isSymbolicLink(current) || !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("Unsafe storage directory.");
                }
            } else {
                Files.createDirectory(current);
            }
        }
    }

    public String toDatabaseRelativePath(Path storedFile) {
        Path rootPath = initializedRoot();
        Path absoluteFile = storedFile.isAbsolute()
                ? storedFile.normalize()
                : rootPath.resolve(storedFile).normalize();
        if (!absoluteFile.startsWith(rootPath) || absoluteFile.equals(rootPath)) {
            throw new IllegalArgumentException("Storage path must remain below the configured root.");
        }
        return rootPath.relativize(absoluteFile).toString().replace('\\', '/');
    }

    /** Strictly reject traversal, absolute/Windows paths, staging references, and symlinks. */
    Path resolveStoredFile(String relativePath) throws IOException {
        if (relativePath == null || !STORED_RELATIVE_PATH.matcher(relativePath).matches()) {
            throw new IOException("Invalid stored file reference.");
        }
        Path rootPath = initializedRoot();
        Path result = rootPath.resolve(relativePath).normalize();
        if (!result.startsWith(rootPath) || result.equals(rootPath)) {
            throw new IOException("Invalid stored file reference.");
        }
        Path parent = rootPath;
        for (Path segment : rootPath.relativize(result)) {
            parent = parent.resolve(segment);
            if (Files.isSymbolicLink(parent)) {
                throw new IOException("Invalid stored file reference.");
            }
        }
        if (!Files.isRegularFile(result, LinkOption.NOFOLLOW_LINKS)
                || !result.toRealPath().startsWith(rootPath)) {
            throw new IOException("File content is unavailable.");
        }
        return result;
    }

    private Path requestDirectory(long requestId, String category) {
        if (requestId < 1) {
            throw new IllegalArgumentException("Request ID must be positive.");
        }
        Path rootPath = initializedRoot();
        Path directory = rootPath.resolve(REQUESTS_DIRECTORY)
                .resolve(Long.toString(requestId)).resolve(category).normalize();
        if (!directory.startsWith(rootPath)) {
            throw new IllegalStateException("Resolved request storage path escaped the configured root.");
        }
        return directory;
    }

    private Path initializedRoot() {
        if (root == null) {
            throw new IllegalStateException("Storage paths have not been initialized.");
        }
        return root;
    }

    /**
     * The documented default is repository-relative even when Maven is started
     * from backend/. Other custom relative paths retain normal process-CWD rules,
     * including target/... locations used by the isolated test suites.
     */
    private static Path resolveConfiguredRoot(Path supplied) {
        Path normalized = supplied.normalize();
        if (!normalized.isAbsolute() && normalized.equals(Path.of("data", "uploads"))) {
            Path current = Path.of("").toAbsolutePath().normalize();
            Path parent = current.getParent();
            if (current.getFileName() != null
                    && current.getFileName().toString().equalsIgnoreCase("backend")
                    && parent != null && Files.isRegularFile(current.resolve("pom.xml"))
                    && Files.isDirectory(parent.resolve("frontend"))
                    && Files.isDirectory(parent.resolve("docs"))) {
                return parent.resolve(normalized).toAbsolutePath().normalize();
            }
        }
        return supplied.toAbsolutePath().normalize();
    }

    private static void createWritableDirectory(Path directory, String description) {
        if (Files.isSymbolicLink(directory)
                || (Files.exists(directory, LinkOption.NOFOLLOW_LINKS)
                && !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS))) {
            throw new IllegalStateException("The configured " + description + " is not a directory.");
        }
        try {
            Files.createDirectories(directory);
            if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)
                    || !Files.isWritable(directory) || hasNoPosixWriteBit(directory)) {
                throw new IllegalStateException("The configured " + description + " is not writable.");
            }
            Path check = Files.createTempFile(directory, ".unidoc-check-", ".tmp");
            Files.delete(check);
        } catch (IOException | SecurityException exception) {
            throw new IllegalStateException("Unable to write to the configured " + description + ".", exception);
        }
    }

    private static boolean hasNoPosixWriteBit(Path directory) {
        try {
            Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(directory);
            return !permissions.contains(PosixFilePermission.OWNER_WRITE)
                    && !permissions.contains(PosixFilePermission.GROUP_WRITE)
                    && !permissions.contains(PosixFilePermission.OTHERS_WRITE);
        } catch (UnsupportedOperationException | IOException exception) {
            return false; // Windows ACLs are covered by isWritable and createTempFile.
        }
    }

    private static void rejectPublicApplicationDirectory(Path candidate) {
        String path = candidate.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
        if (path.contains("/frontend/public/") || path.endsWith("/frontend/public")
                || path.contains("/backend/src/main/resources/static/")
                || path.endsWith("/backend/src/main/resources/static")) {
            throw new IllegalStateException("The storage root cannot be inside a public application directory.");
        }
    }
}
