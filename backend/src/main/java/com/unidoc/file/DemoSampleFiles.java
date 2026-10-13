package com.unidoc.file;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Creates readable, synthetic PDF examples entirely inside protected storage.
 * No user-supplied filenames or paths, no public assets, and no overwrites.
 */
@Component
public final class DemoSampleFiles {
    private final StoragePaths paths;

    public DemoSampleFiles(StoragePaths paths) {
        this.paths = paths;
    }

    public StoredFile ensurePdf(long requestId, String seedNumber, StorageCategory category) {
        if (!seedNumber.matches("UDR-DEMO-[0-9]{4}")
                || (category != StorageCategory.ISSUED && category != StorageCategory.SUPPORTING
                && category != StorageCategory.ADDITIONAL_INFORMATION)) {
            throw new IllegalArgumentException("Unsupported demo fixture.");
        }
        String key = seedNumber + ":" + category.name();
        String filename = UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)) + ".pdf";
        byte[] content = renderPdf(seedNumber, category.name());
        Path directory = switch (category) {
            case SUPPORTING -> paths.supportingDirectory(requestId);
            case ADDITIONAL_INFORMATION -> paths.additionalInformationDirectory(requestId);
            case ISSUED -> paths.issuedDirectory(requestId);
        };
        Path file = directory.resolve(filename);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("Demo files require a transactional seed.");
        }
        try {
            paths.ensureRequestDirectory(directory); // rejects symbolic-link directory ancestors
            if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
                requireMatchingExistingFile(file, content);
            } else {
                try {
                    Files.write(file, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                        @Override
                        public void afterCompletion(int status) {
                            if (status != STATUS_COMMITTED) {
                                try { Files.deleteIfExists(file); }
                                catch (IOException ignored) { /* only seed-created file, never reset cleanup */ }
                            }
                        }
                    });
                } catch (FileAlreadyExistsException conflict) {
                    requireMatchingExistingFile(file, content);
                }
            }
            return new StoredFile("unidoc-demo-" + seedNumber + ".pdf", filename,
                    paths.toDatabaseRelativePath(file), "application/pdf", content.length);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to initialize a protected demo PDF; check storage permissions.", exception);
        }
    }

    private static void requireMatchingExistingFile(Path file, byte[] expected) throws IOException {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                || Files.size(file) != expected.length
                || !Arrays.equals(Files.readAllBytes(file), expected)) {
            throw new IllegalStateException("An existing demo storage path has different content; refusing to overwrite it.");
        }
    }

    /** A minimal standards-shaped single-page PDF with valid byte xref offsets. */
    private static byte[] renderPdf(String number, String category) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        List<Integer> offsets = new ArrayList<>();
        write(bytes, "%PDF-1.4\n%UniDoc synthetic sample\n");
        object(bytes, offsets, 1, "<< /Type /Catalog /Pages 2 0 R >>");
        object(bytes, offsets, 2, "<< /Type /Pages /Kids [3 0 R] /Count 1 >>");
        object(bytes, offsets, 3,
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
                        + "/Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>");
        object(bytes, offsets, 4, "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>");
        String stream = "BT /F1 18 Tf 48 740 Td (UniDoc - Demonstration Only) Tj "
                + "0 -32 Td /F1 12 Tf (" + number + ") Tj "
                + "0 -24 Td (" + category + " synthetic sample document) Tj ET\n";
        byte[] streamBytes = stream.getBytes(StandardCharsets.US_ASCII);
        offsets.add(bytes.size());
        write(bytes, "5 0 obj\n<< /Length " + streamBytes.length + " >>\nstream\n");
        bytes.writeBytes(streamBytes);
        write(bytes, "endstream\nendobj\n");
        int xref = bytes.size();
        write(bytes, "xref\n0 6\n0000000000 65535 f \n");
        for (int position : offsets) write(bytes, "%010d 00000 n \n".formatted(position));
        write(bytes, "trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n" + xref + "\n%%EOF\n");
        return bytes.toByteArray();
    }

    private static void object(ByteArrayOutputStream bytes, List<Integer> offsets, int id, String definition) {
        offsets.add(bytes.size());
        write(bytes, id + " 0 obj\n" + definition + "\nendobj\n");
    }

    private static void write(ByteArrayOutputStream bytes, String text) {
        bytes.writeBytes(text.getBytes(StandardCharsets.US_ASCII));
    }
}
