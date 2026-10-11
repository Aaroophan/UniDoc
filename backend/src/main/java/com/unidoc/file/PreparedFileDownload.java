package com.unidoc.file;

/** Fully read before any download-dependent workflow transition. */
public record PreparedFileDownload(byte[] bytes, String contentType) {
    public PreparedFileDownload {
        bytes = bytes.clone();
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }
}
