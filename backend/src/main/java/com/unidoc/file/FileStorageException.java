package com.unidoc.file;

public class FileStorageException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public FileStorageException() {
        super("The file could not be stored. No successful upload was recorded.");
    }

    public FileStorageException(Throwable cause) {
        super("The file could not be stored. No successful upload was recorded.", cause);
    }
}
