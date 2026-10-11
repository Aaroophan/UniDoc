package com.unidoc.file;

import com.unidoc.common.api.ApiErrorCode;
import com.unidoc.config.StorageProperties;
import com.unidoc.request.IssuedDocumentNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Reusable, workflow-neutral storage boundary. The request service must authorize all
 * reads, check status/ownership before writes, persist StoredFile within its transaction,
 * and call cleanup after a failed transaction/commit. Never expose storage paths in APIs.
 */
@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);
    private static final int SIGNATURE_LENGTH = 8;
    private static final int COPY_BUFFER_SIZE = 16 * 1024;
    private static final int MAX_COLLECTION_FILES = 3;

    private final StoragePaths paths;
    private final StorageProperties properties;
    private final Supplier<UUID> uuidSupplier;

    @Autowired
    public FileStorageService(StoragePaths paths, StorageProperties properties) {
        this(paths, properties, UUID::randomUUID);
    }

    // Deterministic collision and cleanup testing without weakening production UUID selection.
    FileStorageService(StoragePaths paths, StorageProperties properties, Supplier<UUID> uuidSupplier) {
        this.paths = Objects.requireNonNull(paths);
        this.properties = Objects.requireNonNull(properties);
        this.uuidSupplier = Objects.requireNonNull(uuidSupplier);
    }

    /** Reject the entire collection before any staging or database insert. */
    public List<ValidatedUpload> validateSupportingFiles(List<MultipartFile> files, boolean required) {
        List<MultipartFile> provided = files == null ? List.of() : new ArrayList<>(files);
        if (provided.size() > MAX_COLLECTION_FILES) {
            throw new FileValidationException(ApiErrorCode.FILE_COUNT_EXCEEDED);
        }
        if (provided.isEmpty() && required) {
            throw new FileValidationException(ApiErrorCode.SUPPORTING_DOCUMENT_REQUIRED);
        }
        List<ValidatedUpload> validated = new ArrayList<>(provided.size());
        for (MultipartFile file : provided) {
            validated.add(validate(file, properties.supportingMaxBytes(), false));
        }
        return List.copyOf(validated);
    }

    /** One PDF, distinct from the 0-3 image/PDF student collection rules. */
    public ValidatedUpload validateIssuedPdf(MultipartFile file) {
        return validate(file, properties.issuedMaxBytes(), true);
    }

    /** All files in one operation share a category and are removed together on copy failure. */
    public StorageOperation stage(List<ValidatedUpload> validated, StorageCategory category) {
        Objects.requireNonNull(category);
        Objects.requireNonNull(validated);
        if (validated.size() > MAX_COLLECTION_FILES || (category == StorageCategory.ISSUED && validated.size() != 1)) {
            throw new IllegalArgumentException("Invalid file count for the storage category.");
        }
        if (category == StorageCategory.ISSUED && !validated.getFirst().contentType().equals("application/pdf")) {
            throw new IllegalArgumentException("Issued documents must be PDF.");
        }
        StorageOperation operation = new StorageOperation(this, category);
        try {
            for (ValidatedUpload file : validated) {
                stageOne(operation, file);
            }
            log.info("File staging completed operationId={} category={} count={}",
                    operation.operationId, category, operation.staged.size());
            return operation;
        } catch (RuntimeException exception) {
            cleanup(operation);
            throw exception;
        }
    }

    /** Finalize after the caller has obtained the authoritative numeric request ID. */
    public List<StoredFile> finalizeFiles(StorageOperation operation, long requestId) {
        validateOwnedOperation(operation);
        if (requestId < 1) {
            throw new IllegalArgumentException("Request ID must be positive.");
        }
        synchronized (operation) {
            if (operation.finalized || operation.cleaned) {
                throw new IllegalStateException("Storage operation has already been completed.");
            }
            operation.requestId = requestId;
            try {
                Path directory = operation.category.forRequest(paths, requestId);
                if (!operation.staged.isEmpty()) {
                    paths.ensureRequestDirectory(directory);
                }
                List<StoredFile> finalized = new ArrayList<>(operation.staged.size());
                for (StagedEntry entry : operation.staged) {
                    Path destination = directory.resolve(entry.storedFilename).normalize();
                    if (!destination.startsWith(directory)) {
                        throw new IOException("Unsafe final storage path.");
                    }
                    promoteWithoutReplacement(entry.stagedPath, destination, operation);
                    finalized.add(new StoredFile(
                            entry.upload.originalFilename(), entry.storedFilename,
                            paths.toDatabaseRelativePath(destination),
                            entry.upload.contentType(), entry.upload.sizeBytes()));
                }
                operation.finalized = true;
                log.info("File finalization completed operationId={} requestId={} category={} count={}",
                        operation.operationId, requestId, operation.category, finalized.size());
                return List.copyOf(finalized);
            } catch (IOException | SecurityException exception) {
                cleanup(operation);
                throw new FileStorageException(exception);
            } catch (RuntimeException exception) {
                cleanup(operation);
                throw exception;
            }
        }
    }

    /**
     * Compensation is deliberately best effort. It removes only tracked paths created
     * by this operation; failures are WARN-only and cannot shadow a database exception.
     */
    public void cleanup(StorageOperation operation) {
        validateOwnedOperation(operation);
        synchronized (operation) {
            for (Path finalizedPath : operation.finalizedPaths) {
                removeTrackedFile(finalizedPath, operation, "finalized");
            }
            for (StagedEntry entry : operation.staged) {
                removeTrackedFile(entry.stagedPath, operation, "staged");
            }
            operation.cleaned = true;
        }
    }

    /** Call only after the caller's record-level authorization and attachment/request match. */
    public PreparedFileDownload readAuthorized(
            String storedRelativePath, String validatedContentType, long storedSizeBytes
    ) {
        boolean issued = storedRelativePath != null && storedRelativePath.contains("/issued/");
        Path file;
        try {
            file = paths.resolveStoredFile(storedRelativePath);
        } catch (IOException | SecurityException exception) {
            throw missingContent(issued);
        }
        Format format = formatForStoredPath(storedRelativePath);
        if (format == null || !format.contentType.equals(validatedContentType)) {
            throw missingContent(issued);
        }
        long maximum = issued ? properties.issuedMaxBytes() : properties.supportingMaxBytes();
        if (storedSizeBytes <= 0 || storedSizeBytes > maximum) {
            throw new FileStorageException();
        }
        try (InputStream input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = input.readNBytes(Math.toIntExact(maximum) + 1);
            if (bytes.length != storedSizeBytes || bytes.length > maximum
                    || !format.hasSignature(Arrays.copyOf(bytes, Math.min(bytes.length, SIGNATURE_LENGTH)))) {
                throw new FileStorageException();
            }
            return new PreparedFileDownload(bytes, format.contentType);
        } catch (IOException exception) {
            throw new FileStorageException(exception);
        }
    }

    private ValidatedUpload validate(MultipartFile file, long maxBytes, boolean issued) {
        if (file == null || file.isEmpty() || file.getSize() == 0) {
            throw new FileValidationException(ApiErrorCode.FILE_EMPTY);
        }
        if (maxBytes <= 0 || file.getSize() > maxBytes) {
            throw new FileValidationException(ApiErrorCode.FILE_TOO_LARGE);
        }
        String mime = file.getContentType() == null ? ""
                : file.getContentType().strip().toLowerCase(Locale.ROOT);
        String supplied = file.getOriginalFilename();
        String sanitized = SanitizedFileNames.sanitize(supplied, "");
        String extension = extensionOf(sanitized);
        Format expected = Format.fromClientExtension(extension);
        byte[] header;
        try (InputStream input = file.getInputStream()) {
            header = input.readNBytes(SIGNATURE_LENGTH);
        } catch (IOException exception) {
            throw new FileStorageException(exception);
        }
        // An absent basename is the only case in which a type-specific display fallback
        // replaces the filename. An existing, unsupported extension is always rejected.
        if (sanitized.isEmpty()) {
            expected = Format.fromMime(mime);
            if (expected != null) {
                sanitized = (issued ? "document" : "attachment") + "." + expected.storedExtension;
            }
        }
        if (expected == null || !expected.contentType.equals(mime)
                || !expected.hasSignature(header) || (issued && expected != Format.PDF)) {
            throw new FileValidationException(ApiErrorCode.UNSUPPORTED_FILE_TYPE);
        }
        return new ValidatedUpload(file, sanitized, expected.storedExtension, expected.contentType, file.getSize());
    }

    private void stageOne(StorageOperation operation, ValidatedUpload file) {
        // Re-check approved type and the bounded stream to avoid a modified MultipartFile
        // silently storing different bytes between validation and staging.
        Format format = Format.fromStoredExtension(file.storedExtension());
        long limit = operation.category == StorageCategory.ISSUED
                ? properties.issuedMaxBytes() : properties.supportingMaxBytes();
        if (format == null || !format.contentType.equals(file.contentType()) || file.sizeBytes() > limit) {
            throw new FileValidationException(ApiErrorCode.UNSUPPORTED_FILE_TYPE);
        }
        try {
            paths.verifyTemporaryDirectory();
        } catch (IOException exception) {
            throw new FileStorageException(exception);
        }
        String storedFilename = uuidSupplier.get().toString().toLowerCase(Locale.ROOT)
                + "." + format.storedExtension;
        Path staged = paths.temporaryDirectory().resolve(storedFilename);
        if (!staged.normalize().startsWith(paths.temporaryDirectory())) {
            throw new FileStorageException();
        }
        try (InputStream input = file.source().getInputStream();
             OutputStream output = Files.newOutputStream(staged,
                     StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
            // Track only AFTER CREATE_NEW succeeds: never remove a pre-existing collision.
            operation.staged.add(new StagedEntry(staged, storedFilename, file));
            byte[] buffer = new byte[COPY_BUFFER_SIZE];
            byte[] prefix = new byte[SIGNATURE_LENGTH];
            int prefixCount = 0;
            long total = 0;
            int count;
            while ((count = input.read(buffer)) != -1) {
                total += count;
                if (total > limit) {
                    throw new FileValidationException(ApiErrorCode.FILE_TOO_LARGE);
                }
                if (prefixCount < SIGNATURE_LENGTH) {
                    int take = Math.min(count, SIGNATURE_LENGTH - prefixCount);
                    System.arraycopy(buffer, 0, prefix, prefixCount, take);
                    prefixCount += take;
                }
                output.write(buffer, 0, count);
            }
            if (total == 0) {
                throw new FileValidationException(ApiErrorCode.FILE_EMPTY);
            }
            if (total != file.sizeBytes() || !format.hasSignature(Arrays.copyOf(prefix, prefixCount))) {
                throw new FileValidationException(ApiErrorCode.UNSUPPORTED_FILE_TYPE);
            }
        } catch (IOException | SecurityException exception) {
            throw new FileStorageException(exception);
        }
    }

    /**
     * An atomic create-new hard link is preferred because an atomic Files.move may
     * replace an existing target on some providers even without REPLACE_EXISTING.
     * Both directories are under one root. Providers without hard-link support use
     * a replacement-free Files.move fallback.
     */
    private static void promoteWithoutReplacement(Path staged, Path target, StorageOperation operation)
            throws IOException {
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new FileAlreadyExistsException("Final storage filename collision.");
        }
        try {
            Files.createLink(target, staged); // atomic create-new; never replaces an existing file
            operation.finalizedPaths.add(target); // tracked before the staged-name removal
            Files.delete(staged);
            return;
        } catch (FileAlreadyExistsException exception) {
            throw exception;
        } catch (UnsupportedOperationException exception) {
            // Filesystems without hard-link support use a non-replacing move below.
        } catch (IOException exception) {
            if (operation.finalizedPaths.contains(target)
                    || Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                // The caller's cleanup will try any link we successfully created.
                throw exception;
            }
            // An unsupported cross-device link may work with a regular move.
        }
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new FileAlreadyExistsException("Final storage filename collision.");
        }
        Files.move(staged, target); // fallback without REPLACE_EXISTING
        operation.finalizedPaths.add(target);
    }

    private void removeTrackedFile(Path file, StorageOperation operation, String stage) {
        try {
            // Cleanup handles originate in this service, never from a request or DB path.
            Files.deleteIfExists(file);
        } catch (IOException | SecurityException exception) {
            log.warn("File cleanup failed operationId={} requestId={} category={} stage={} error={}",
                    operation.operationId, operation.requestId, operation.category, stage,
                    exception.getClass().getSimpleName());
        }
    }

    private void validateOwnedOperation(StorageOperation operation) {
        if (operation == null || operation.owner != this) {
            throw new IllegalArgumentException("Invalid storage operation handle.");
        }
    }

    private static RuntimeException missingContent(boolean issued) {
        return issued ? new IssuedDocumentNotFoundException() : new AttachmentNotFoundException();
    }

    private static Format formatForStoredPath(String path) {
        return Format.fromStoredExtension(extensionOf(path));
    }

    private static String extensionOf(String name) {
        if (name == null) {
            return "";
        }
        int dot = name.lastIndexOf('.');
        return dot <= 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** Handle whose paths and ownership cannot be supplied by an API caller. */
    public static final class StorageOperation {
        private final FileStorageService owner;
        private final UUID operationId = UUID.randomUUID();
        private final StorageCategory category;
        private final List<StagedEntry> staged = new ArrayList<>();
        private final List<Path> finalizedPaths = new ArrayList<>();
        private Long requestId;
        private boolean finalized;
        private boolean cleaned;

        private StorageOperation(FileStorageService owner, StorageCategory category) {
            this.owner = owner;
            this.category = category;
        }

        public UUID operationId() {
            return operationId;
        }

        public StorageCategory category() {
            return category;
        }

        public synchronized int stagedCount() {
            return staged.size();
        }
    }

    private record StagedEntry(Path stagedPath, String storedFilename, ValidatedUpload upload) {
    }

    private enum Format {
        PDF("pdf", "application/pdf", new byte[]{'%','P','D','F','-'}),
        JPEG("jpg", "image/jpeg", new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff}),
        PNG("png", "image/png", new byte[]{(byte) 0x89, 'P','N','G', 0x0d, 0x0a, 0x1a, 0x0a});

        private final String storedExtension;
        private final String contentType;
        private final byte[] magic;

        Format(String storedExtension, String contentType, byte[] magic) {
            this.storedExtension = storedExtension;
            this.contentType = contentType;
            this.magic = magic;
        }

        boolean hasSignature(byte[] prefix) {
            if (prefix.length < magic.length) {
                return false;
            }
            for (int i = 0; i < magic.length; i++) {
                if (prefix[i] != magic[i]) {
                    return false;
                }
            }
            return true;
        }

        static Format fromClientExtension(String extension) {
            if ("jpeg".equals(extension)) return JPEG;
            return fromStoredExtension(extension);
        }

        static Format fromStoredExtension(String extension) {
            for (Format format : values()) {
                if (format.storedExtension.equals(extension)) return format;
            }
            return null;
        }

        static Format fromMime(String mime) {
            for (Format format : values()) {
                if (format.contentType.equals(mime)) return format;
            }
            return null;
        }
    }
}
