package com.unidoc.file;

import com.unidoc.config.StorageProperties;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Assumptions;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StorageConfigurationTests {

    @TempDir
    Path temporaryRoot;

    @Test
    void configurationLimitsRejectNegativeZeroAndUnreasonableValues() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            assertThat(validator.validate(new StorageProperties(temporaryRoot, 0, 10_485_760))).isNotEmpty();
            assertThat(validator.validate(new StorageProperties(temporaryRoot, -10, 10_485_760))).isNotEmpty();
            assertThat(validator.validate(new StorageProperties(temporaryRoot, 5_242_880, 10_485_761))).isNotEmpty();
            assertThat(validator.validate(new StorageProperties(temporaryRoot, 5_242_880, 10_485_760))).isEmpty();
        }
    }

    @Test
    void rootIsResolvedAndPublicRootsAreRejected() throws Exception {
        Path configured = temporaryRoot.resolve("a/../uploads");
        StoragePaths paths = new StoragePaths(new StorageProperties(configured, 5_242_880, 10_485_760));
        paths.initialize();
        assertThat(paths.storageRoot()).isEqualTo(temporaryRoot.resolve("uploads").toRealPath());
        assertThat(Files.isDirectory(paths.temporaryDirectory())).isTrue();
        Path publicDir = temporaryRoot.resolve("frontend/public/uploads");
        assertThatThrownBy(() -> new StoragePaths(
                new StorageProperties(publicDir, 5_242_880, 10_485_760)).initialize())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsNonWritableDirectoryOnPosix() throws Exception {
        Assumptions.assumeTrue(Files.getFileStore(temporaryRoot).supportsFileAttributeView("posix"));
        Path unwritable = Files.createDirectory(temporaryRoot.resolve("unwritable"));
        Set<PosixFilePermission> original = Files.getPosixFilePermissions(unwritable);
        try {
            Files.setPosixFilePermissions(unwritable, Set.of(PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_EXECUTE));
            assertThatThrownBy(() -> new StoragePaths(new StorageProperties(
                    unwritable, 5_242_880, 10_485_760)).initialize())
                    .isInstanceOf(IllegalStateException.class);
        } finally {
            Files.setPosixFilePermissions(unwritable, original);
        }
    }

    @Test
    void rejectsSymbolicLinkRootWhenAvailable() throws Exception {
        Path real = Files.createDirectory(temporaryRoot.resolve("real"));
        Path link = temporaryRoot.resolve("link");
        try {
            Files.createSymbolicLink(link, real);
        } catch (UnsupportedOperationException | java.io.IOException | SecurityException exception) {
            Assumptions.assumeTrue(false, "Symbolic links are unavailable on this host.");
        }
        assertThatThrownBy(() -> new StoragePaths(new StorageProperties(
                link, 5_242_880, 10_485_760)).initialize())
                .isInstanceOf(IllegalStateException.class);
    }
}
