package com.unidoc.file;

import com.unidoc.common.api.ApiErrorCode;
import com.unidoc.config.StorageProperties;
import com.unidoc.request.IssuedDocumentNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Assumptions;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileStorageServiceTests {

    private static final long SUPPORT_MAX = 5_242_880;
    private static final long ISSUED_MAX = 10_485_760;
    private static final byte[] PDF = "%PDF-1.7\nexample".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    private static final byte[] JPEG = {(byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xe0, 0, 1};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0, 1};

    @TempDir
    Path temporaryRoot;

    private StoragePaths paths;
    private FileStorageService storage;

    @BeforeEach
    void setup() {
        StorageProperties properties = new StorageProperties(temporaryRoot.resolve("uploads"), SUPPORT_MAX, ISSUED_MAX);
        paths = new StoragePaths(properties);
        paths.initialize();
        storage = new FileStorageService(paths, properties);
    }

    private static MockMultipartFile pdf(String name, byte[] bytes) {
        return new MockMultipartFile("files", name, "application/pdf", bytes);
    }

    private static MockMultipartFile jpeg(String name, byte[] bytes) {
        return new MockMultipartFile("files", name, "image/jpeg", bytes);
    }

    private static MockMultipartFile png(String name, byte[] bytes) {
        return new MockMultipartFile("files", name, "image/png", bytes);
    }

    private static byte[] padded(byte[] signature, int desiredSize) {
        byte[] bytes = Arrays.copyOf(signature, desiredSize);
        return bytes;
    }

    private static void invalidFileCode(Runnable action, ApiErrorCode expected) {
        assertThatThrownBy(action::run).isInstanceOf(FileValidationException.class)
                .satisfies(error -> assertThat(((FileValidationException) error).getCode()).isEqualTo(expected));
    }

    @Test
    void validatesAllThreeTypesAndNormalizesJpegStorageExtension() {
        List<ValidatedUpload> list = storage.validateSupportingFiles(List.of(
                pdf("c:/secret/Report.PDF", PDF), jpeg("C:\\tmp\\ID.JPEG", JPEG), png("image.png", PNG)
        ), true);
        assertThat(list).hasSize(3);
        assertThat(list).extracting(ValidatedUpload::storedExtension).containsExactly("pdf", "jpg", "png");
        assertThat(list).extracting(ValidatedUpload::originalFilename)
                .containsExactly("Report.PDF", "ID.JPEG", "image.png");
        assertThat(list).extracting(ValidatedUpload::contentType)
                .containsExactly("application/pdf", "image/jpeg", "image/png");
    }

    @Test
    void acceptsAbsentOptionalCollectionRejectsMissingRequiredCollection() {
        assertThat(storage.validateSupportingFiles(null, false)).isEmpty();
        assertThat(storage.validateSupportingFiles(List.of(), false)).isEmpty();
        invalidFileCode(() -> storage.validateSupportingFiles(null, true), ApiErrorCode.SUPPORTING_DOCUMENT_REQUIRED);
        invalidFileCode(() -> storage.validateSupportingFiles(List.of(), true), ApiErrorCode.SUPPORTING_DOCUMENT_REQUIRED);
    }

    @Test
    void rejectsFourthFileBeforeWritingAnything() throws Exception {
        invalidFileCode(() -> storage.validateSupportingFiles(List.of(pdf("1.pdf", PDF), pdf("2.pdf", PDF),
                pdf("3.pdf", PDF), pdf("4.pdf", PDF)), false), ApiErrorCode.FILE_COUNT_EXCEEDED);
        try (Stream<Path> files = Files.list(paths.temporaryDirectory())) {
            assertThat(files.toList()).isEmpty();
        }
    }

    @Test
    void rejectsNullOrEmptyFile() {
        invalidFileCode(() -> storage.validateIssuedPdf(null), ApiErrorCode.FILE_EMPTY);
        invalidFileCode(() -> storage.validateSupportingFiles(List.of(pdf("empty.pdf", new byte[0])), false),
                ApiErrorCode.FILE_EMPTY);
        invalidFileCode(() -> storage.validateSupportingFiles(Arrays.asList((MultipartFile) null), false),
                ApiErrorCode.FILE_EMPTY);
    }

    @Test
    void checksExactSupportingAndIssuedByteBoundaries() {
        assertThat(storage.validateSupportingFiles(List.of(pdf("limit.pdf", padded(PDF, (int) SUPPORT_MAX))), false))
                .singleElement().extracting(ValidatedUpload::sizeBytes).isEqualTo(SUPPORT_MAX);
        invalidFileCode(() -> storage.validateSupportingFiles(
                List.of(pdf("over.pdf", padded(PDF, (int) SUPPORT_MAX + 1))), false),
                ApiErrorCode.FILE_TOO_LARGE);
        assertThat(storage.validateIssuedPdf(pdf("limit.pdf", padded(PDF, (int) ISSUED_MAX))).sizeBytes())
                .isEqualTo(ISSUED_MAX);
        invalidFileCode(() -> storage.validateIssuedPdf(pdf("over.pdf", padded(PDF, (int) ISSUED_MAX + 1))),
                ApiErrorCode.FILE_TOO_LARGE);
    }

    @Test
    void rejectsDisguisedTextMimeMismatchAndIncorrectExtension() {
        invalidFileCode(() -> storage.validateIssuedPdf(pdf("issued.pdf", "not PDF".getBytes())),
                ApiErrorCode.UNSUPPORTED_FILE_TYPE);
        invalidFileCode(() -> storage.validateSupportingFiles(List.of(
                new MockMultipartFile("files", "scan.pdf", "image/png", PDF)), false),
                ApiErrorCode.UNSUPPORTED_FILE_TYPE);
        invalidFileCode(() -> storage.validateSupportingFiles(List.of(
                new MockMultipartFile("files", "photo.jpg", "image/jpeg", PNG)), false),
                ApiErrorCode.UNSUPPORTED_FILE_TYPE);
        invalidFileCode(() -> storage.validateSupportingFiles(List.of(
                new MockMultipartFile("files", "file.exe", "application/pdf", PDF)), false),
                ApiErrorCode.UNSUPPORTED_FILE_TYPE);
        invalidFileCode(() -> storage.validateSupportingFiles(List.of(
                new MockMultipartFile("files", "file.pdf", "application/octet-stream", PDF)), false),
                ApiErrorCode.UNSUPPORTED_FILE_TYPE);
        invalidFileCode(() -> storage.validateIssuedPdf(png("not-an-issued.pdf", PNG)),
                ApiErrorCode.UNSUPPORTED_FILE_TYPE);
    }

    @Test
    void usesSafeFallbackOnlyForAbsentBasename() {
        ValidatedUpload blank = storage.validateIssuedPdf(pdf(null, PDF));
        assertThat(blank.originalFilename()).isEqualTo("document.pdf");
        invalidFileCode(() -> storage.validateIssuedPdf(pdf("filename", PDF)),
                ApiErrorCode.UNSUPPORTED_FILE_TYPE);
    }

    @Test
    void validatesWholeCollectionBeforeStaging() throws Exception {
        invalidFileCode(() -> storage.validateSupportingFiles(List.of(pdf("valid.pdf", PDF),
                pdf("invalid.pdf", "x".getBytes())), false), ApiErrorCode.UNSUPPORTED_FILE_TYPE);
        try (Stream<Path> files = Files.list(paths.temporaryDirectory())) {
            assertThat(files.toList()).isEmpty();
        }
    }

    @Test
    void stagesUuidOnlyAndFinalizesAllThreeCategoriesWithRelativeMetadata() throws Exception {
        List<ValidatedUpload> uploads = storage.validateSupportingFiles(List.of(
                pdf("../private-info.pdf", PDF), jpeg("C:\\users\\name\\face.jpg", JPEG)), true);
        var first = storage.stage(uploads, StorageCategory.SUPPORTING);
        assertThat(first.stagedCount()).isEqualTo(2);
        try (Stream<Path> files = Files.list(paths.temporaryDirectory())) {
            assertThat(files.count()).isEqualTo(2);
        }
        List<StoredFile> supporting = storage.finalizeFiles(first, 42);
        assertThat(supporting).hasSize(2);
        assertThat(supporting).allSatisfy(file -> {
            assertThat(file.storedFilename()).matches("[0-9a-f-]{36}\\.(pdf|jpg)");
            assertThat(file.relativePath()).startsWith("requests/42/supporting/");
            assertThat(file.relativePath()).doesNotContain("private-info", "users", "\\");
            assertThat(Files.exists(paths.storageRoot().resolve(file.relativePath()))).isTrue();
        });
        var additional = storage.stage(storage.validateSupportingFiles(List.of(png("info.png", PNG)), false),
                StorageCategory.ADDITIONAL_INFORMATION);
        assertThat(storage.finalizeFiles(additional, 42).getFirst().relativePath())
                .startsWith("requests/42/additional-information/");
        var issued = storage.stage(List.of(storage.validateIssuedPdf(pdf("ready.pdf", PDF))), StorageCategory.ISSUED);
        assertThat(storage.finalizeFiles(issued, 42).getFirst().relativePath())
                .startsWith("requests/42/issued/");
        try (Stream<Path> files = Files.list(paths.temporaryDirectory())) {
            assertThat(files.toList()).isEmpty();
        }
    }

    @Test
    void fileContentsAndNamesAreNotOverwrittenByUuidCollision() throws Exception {
        UUID uuid = UUID.fromString("12345678-1234-1234-1234-123456789abc");
        FileStorageService deterministic = new FileStorageService(paths,
                new StorageProperties(paths.storageRoot(), SUPPORT_MAX, ISSUED_MAX), () -> uuid);
        ValidatedUpload validated = deterministic.validateIssuedPdf(pdf("a.pdf", PDF));
        var original = deterministic.stage(List.of(validated), StorageCategory.ISSUED);
        // A second staging attempt must NOT remove the first operation's bytes.
        assertThatThrownBy(() -> deterministic.stage(List.of(validated), StorageCategory.ISSUED))
                .isInstanceOf(FileStorageException.class);
        Path stagedPath = paths.temporaryDirectory().resolve(uuid + ".pdf");
        assertThat(Files.readAllBytes(stagedPath)).isEqualTo(PDF);
        Path target = paths.issuedDirectory(9).resolve(uuid + ".pdf");
        Files.createDirectories(target.getParent());
        byte[] existing = "preserve existing".getBytes();
        Files.write(target, existing);
        assertThatThrownBy(() -> deterministic.finalizeFiles(original, 9))
                .isInstanceOf(FileStorageException.class);
        assertThat(Files.readAllBytes(target)).isEqualTo(existing);
        assertThat(Files.exists(stagedPath)).isFalse();
    }

    @Test
    void failedLaterCopyRemovesAllAlreadyStagedFiles() throws Exception {
        MockMultipartFile first = pdf("okay.pdf", PDF);
        AtomicInteger streamsOpened = new AtomicInteger();
        MockMultipartFile second = new MockMultipartFile("files", "later.pdf", "application/pdf", PDF) {
            @Override
            public InputStream getInputStream() throws IOException {
                if (streamsOpened.incrementAndGet() == 1) return super.getInputStream();
                throw new IOException("Simulated staging stream failure");
            }
        };
        var validated = storage.validateSupportingFiles(List.of(first, second), false);
        assertThatThrownBy(() -> storage.stage(validated, StorageCategory.SUPPORTING))
                .isInstanceOf(FileStorageException.class);
        try (Stream<Path> files = Files.list(paths.temporaryDirectory())) {
            assertThat(files.toList()).isEmpty();
        }
    }

    @Test
    void failedFinalMoveCompensatesAndLeavesNoStagedFiles() throws Exception {
        var staged = storage.stage(storage.validateSupportingFiles(List.of(pdf("a.pdf", PDF)), false),
                StorageCategory.SUPPORTING);
        Path blockingFile = paths.storageRoot().resolve("requests/12");
        Files.createDirectories(blockingFile.getParent());
        Files.write(blockingFile, PDF);
        assertThatThrownBy(() -> storage.finalizeFiles(staged, 12))
                .isInstanceOf(FileStorageException.class);
        try (Stream<Path> files = Files.list(paths.temporaryDirectory())) {
            assertThat(files.toList()).isEmpty();
        }
    }

    @Test
    void compensationRemovesOnlyOwnFilesAndCanBeCalledTwice() throws Exception {
        var one = storage.stage(storage.validateSupportingFiles(List.of(pdf("a.pdf", PDF)), false),
                StorageCategory.SUPPORTING);
        var two = storage.stage(storage.validateSupportingFiles(List.of(pdf("b.pdf", PDF)), false),
                StorageCategory.SUPPORTING);
        StoredFile first = storage.finalizeFiles(one, 80).getFirst();
        StoredFile separate = storage.finalizeFiles(two, 80).getFirst();
        Path userFile = paths.storageRoot().resolve(first.relativePath());
        Path otherFile = paths.storageRoot().resolve(separate.relativePath());
        storage.cleanup(one);
        storage.cleanup(one);
        assertThat(Files.exists(userFile)).isFalse();
        assertThat(Files.exists(otherFile)).isTrue();
        assertThatThrownBy(() -> storage.finalizeFiles(one, 80)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void failedSecondMoveCompensatesFirstAndPreservesCollisionTarget() throws Exception {
        UUID a = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID b = UUID.fromString("22222222-2222-2222-2222-222222222222");
        AtomicInteger index = new AtomicInteger();
        FileStorageService deterministic = new FileStorageService(paths,
                new StorageProperties(paths.storageRoot(), SUPPORT_MAX, ISSUED_MAX),
                () -> index.getAndIncrement() == 0 ? a : b);
        var operation = deterministic.stage(deterministic.validateSupportingFiles(List.of(
                pdf("one.pdf", PDF), pdf("two.pdf", PDF)), false), StorageCategory.SUPPORTING);
        Path destination = paths.supportingDirectory(31);
        Files.createDirectories(destination);
        Path preexisting = destination.resolve(b + ".pdf");
        Files.write(preexisting, "old content".getBytes());
        assertThatThrownBy(() -> deterministic.finalizeFiles(operation, 31))
                .isInstanceOf(FileStorageException.class);
        assertThat(Files.exists(destination.resolve(a + ".pdf"))).isFalse();
        assertThat(Files.readString(preexisting)).isEqualTo("old content");
        try (Stream<Path> tempFiles = Files.list(paths.temporaryDirectory())) {
            assertThat(tempFiles.toList()).isEmpty();
        }
    }

    @Test
    void cleanupContinuesWhenOneDeletionFails() throws Exception {
        var operation = storage.stage(storage.validateSupportingFiles(List.of(
                pdf("first.pdf", PDF), pdf("second.pdf", PDF)), false), StorageCategory.SUPPORTING);
        var finalFiles = storage.finalizeFiles(operation, 44);
        Path first = paths.storageRoot().resolve(finalFiles.get(0).relativePath());
        Path second = paths.storageRoot().resolve(finalFiles.get(1).relativePath());
        Files.delete(first);
        Files.createDirectory(first);
        Files.write(first.resolve("marker"), "keep".getBytes());
        storage.cleanup(operation);
        assertThat(Files.exists(first.resolve("marker"))).isTrue();
        assertThat(Files.exists(second)).isFalse();
    }

    @Test
    void authorizedReadGetsFullValidatedBytesAndProtectsContents() {
        var staged = storage.stage(List.of(storage.validateIssuedPdf(pdf("ready.pdf", PDF))), StorageCategory.ISSUED);
        StoredFile stored = storage.finalizeFiles(staged, 19).getFirst();
        PreparedFileDownload download = storage.readAuthorized(
                stored.relativePath(), stored.contentType(), stored.sizeBytes());
        assertThat(download.contentType()).isEqualTo("application/pdf");
        assertThat(download.bytes()).isEqualTo(PDF);
        byte[] modified = download.bytes();
        modified[0] = 0;
        assertThat(download.bytes()).isEqualTo(PDF);
    }

    @Test
    void readRejectsUnsafeReferencesMissingBytesAndMismatch() throws Exception {
        assertThatThrownBy(() -> storage.readAuthorized("../../secrets.txt", "application/pdf", 10))
                .isInstanceOf(AttachmentNotFoundException.class);
        assertThatThrownBy(() -> storage.readAuthorized("C:\\Windows\\secret.pdf", "application/pdf", 10))
                .isInstanceOf(AttachmentNotFoundException.class);
        assertThatThrownBy(() -> storage.readAuthorized("requests/1/issued/"
                + UUID.randomUUID() + ".pdf", "application/pdf", PDF.length))
                .isInstanceOf(IssuedDocumentNotFoundException.class);
        assertThatThrownBy(() -> storage.readAuthorized(".tmp/secret.pdf", "application/pdf", 10))
                .isInstanceOf(AttachmentNotFoundException.class);
        var staged = storage.stage(storage.validateSupportingFiles(List.of(pdf("a.pdf", PDF)), false),
                StorageCategory.SUPPORTING);
        StoredFile stored = storage.finalizeFiles(staged, 23).getFirst();
        assertThatThrownBy(() -> storage.readAuthorized(stored.relativePath(), "image/png", stored.sizeBytes()))
                .isInstanceOf(AttachmentNotFoundException.class);
        Files.write(paths.storageRoot().resolve(stored.relativePath()), "tampered".getBytes());
        assertThatThrownBy(() -> storage.readAuthorized(stored.relativePath(), "application/pdf", stored.sizeBytes()))
                .isInstanceOf(FileStorageException.class);
    }

    @Test
    void stagingRejectsATemporaryDirectoryReplacedBySymlink() throws Exception {
        Path tmp = paths.temporaryDirectory();
        Path elsewhere = Files.createDirectory(temporaryRoot.resolve("outside"));
        Files.delete(tmp);
        try {
            Files.createSymbolicLink(tmp, elsewhere);
        } catch (UnsupportedOperationException | IOException | SecurityException exception) {
            Assumptions.assumeTrue(false, "Symbolic links are unavailable on this host.");
        }
        var validated = storage.validateSupportingFiles(List.of(pdf("safe.pdf", PDF)), false);
        assertThatThrownBy(() -> storage.stage(validated, StorageCategory.SUPPORTING))
                .isInstanceOf(FileStorageException.class);
        try (Stream<Path> outsideFiles = Files.list(elsewhere)) {
            assertThat(outsideFiles.toList()).isEmpty();
        }
    }

    @Test
    void readsValidatedJpegAndPngWithTheirCanonicalMimeTypes() throws Exception {
        for (MockMultipartFile source : List.of(jpeg("a.jpeg", JPEG), png("b.png", PNG))) {
            var staged = storage.stage(storage.validateSupportingFiles(List.of(source), false),
                    StorageCategory.ADDITIONAL_INFORMATION);
            StoredFile stored = storage.finalizeFiles(staged, 48).getFirst();
            var prepared = storage.readAuthorized(stored.relativePath(), stored.contentType(), stored.sizeBytes());
            assertThat(prepared.bytes()).isEqualTo(source.getBytes());
            assertThat(prepared.contentType()).isEqualTo(source.getContentType());
        }
    }

    @Test
    void symlinkedFinalContentIsRejectedWhereSupported() throws Exception {
        var staged = storage.stage(storage.validateSupportingFiles(List.of(pdf("a.pdf", PDF)), false),
                StorageCategory.SUPPORTING);
        StoredFile stored = storage.finalizeFiles(staged, 27).getFirst();
        Path target = paths.storageRoot().resolve(stored.relativePath());
        Path other = Files.write(temporaryRoot.resolve("secret.pdf"), PDF);
        Files.delete(target);
        try {
            Files.createSymbolicLink(target, other);
        } catch (UnsupportedOperationException | IOException | SecurityException exception) {
            Assumptions.assumeTrue(false, "Symbolic links are unavailable on this host.");
        }
        assertThatThrownBy(() -> storage.readAuthorized(stored.relativePath(), stored.contentType(), stored.sizeBytes()))
                .isInstanceOf(AttachmentNotFoundException.class);
    }
}
