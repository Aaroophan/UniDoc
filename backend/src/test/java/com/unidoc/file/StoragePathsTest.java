package com.unidoc.file;

import com.unidoc.config.StorageProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StoragePathsTest {

    @TempDir
    Path temporaryRoot;

    @Test
    void initializesDirectoriesAndBuildsRequestPaths() {
        Path configuredRoot = temporaryRoot.resolve("uploads");
        StoragePaths paths = new StoragePaths(new StorageProperties(configuredRoot, 5_242_880, 10_485_760));

        paths.initialize();

        assertThat(configuredRoot).isDirectory();
        assertThat(paths.temporaryDirectory()).isDirectory();
        assertThat(paths.supportingDirectory(42)).isEqualTo(configuredRoot.resolve("requests/42/supporting"));
        assertThat(paths.additionalInformationDirectory(42))
                .isEqualTo(configuredRoot.resolve("requests/42/additional-information"));
        assertThat(paths.issuedDirectory(42)).isEqualTo(configuredRoot.resolve("requests/42/issued"));
        assertThat(paths.toDatabaseRelativePath(paths.issuedDirectory(42).resolve("document.pdf")))
                .isEqualTo("requests/42/issued/document.pdf");
    }

    @Test
    void rejectsARegularFileAsTheStorageRoot() throws Exception {
        Path configuredFile = Files.createFile(temporaryRoot.resolve("uploads"));
        StoragePaths paths = new StoragePaths(new StorageProperties(configuredFile, 5_242_880, 10_485_760));

        assertThatThrownBy(paths::initialize)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("The configured storage root is not a directory.");
    }
}
