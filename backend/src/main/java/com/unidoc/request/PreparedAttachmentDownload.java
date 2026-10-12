package com.unidoc.request;

/** All file bytes are prepared after authorization, before HTTP response headers are built. */
public record PreparedAttachmentDownload(byte[] bytes, String displayFilename, String contentType) {
    public PreparedAttachmentDownload {
        bytes = bytes.clone();
    }
    @Override
    public byte[] bytes() {
        return bytes.clone();
    }
}
