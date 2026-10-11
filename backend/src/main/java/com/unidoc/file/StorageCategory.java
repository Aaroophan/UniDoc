package com.unidoc.file;

import java.nio.file.Path;

/** Server-selected workflow category; never parsed from a client request. */
public enum StorageCategory {
    SUPPORTING("supporting"),
    ADDITIONAL_INFORMATION("additional-information"),
    ISSUED("issued");

    private final String directoryName;

    StorageCategory(String directoryName) {
        this.directoryName = directoryName;
    }

    public String directoryName() {
        return directoryName;
    }

    Path forRequest(StoragePaths paths, long requestId) {
        return switch (this) {
            case SUPPORTING -> paths.supportingDirectory(requestId);
            case ADDITIONAL_INFORMATION -> paths.additionalInformationDirectory(requestId);
            case ISSUED -> paths.issuedDirectory(requestId);
        };
    }
}
