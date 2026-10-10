package com.unidoc;

import com.unidoc.file.StoragePaths;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "UNIDOC_DB_URL=jdbc:h2:mem:unidoc;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "UNIDOC_DB_USERNAME=sa",
        "UNIDOC_DB_PASSWORD=",
        "UNIDOC_JWT_SECRET=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "UNIDOC_STORAGE_ROOT=target/context-test-uploads",
        "spring.datasource.driver-class-name=org.h2.Driver"
})
class UniDocApplicationTest {

    @Autowired
    private StoragePaths storagePaths;

    @Test
    void loadsTheConfiguredApplicationAndInitializesStorage() {
        assertThat(storagePaths.temporaryDirectory()).isDirectory();
    }
}
