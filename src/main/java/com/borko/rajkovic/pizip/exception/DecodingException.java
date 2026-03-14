package com.borko.rajkovic.pizip.exception;

public class DecodingException extends PiZipException {

    public DecodingException(String message) {
        super(message);
    }

    public DecodingException(String message, Throwable cause) {
        super(message, cause);
    }
}
