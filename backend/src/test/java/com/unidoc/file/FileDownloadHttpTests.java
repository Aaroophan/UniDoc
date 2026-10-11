package com.unidoc.file;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Test-only route: Issue 11/15 will put authorization before this header builder. */
class FileDownloadHttpTests {

    @RestController
    public static class HeaderTestController {
        @GetMapping("/test/file-response")
        public ResponseEntity<byte[]> download() {
            byte[] contents = "%PDF-1.7\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            return ResponseEntity.ok()
                    .headers(FileDownloadHeaders.forDownload("a\"\r\nb.pdf", "application/pdf", contents.length))
                    .body(contents);
        }
    }

    @Test
    void fileResponseHasRequiredSafeHeadersAndBytes() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new HeaderTestController()).build();
        mvc.perform(get("/test/file-response"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(header().string("Pragma", "no-cache"))
                .andExpect(header().exists("Content-Disposition"))
                .andExpect(content().bytes("%PDF-1.7\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
    }
}
