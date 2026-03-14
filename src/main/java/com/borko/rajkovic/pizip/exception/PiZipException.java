package com.borko.rajkovic.pizip.exception;

public class PiZipException extends RuntimeException {

    public PiZipException(String message) {
        super(message);
    }

    public PiZipException(String message, Throwable cause) {
        super(message, cause);
    }
}
