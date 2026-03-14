package com.borko.rajkovic.pizip.controller;

import com.borko.rajkovic.pizip.exception.DecodingException;
import com.borko.rajkovic.pizip.exception.PiZipException;
import com.borko.rajkovic.pizip.model.PiZipDTOs.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class PiZipExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(PiZipExceptionHandler.class);

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMediaTypeException.class})
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));

        log.debug("Validation failure on {}: {}", request.getRequestURI(), message);

        return ResponseEntity.badRequest().body(new ErrorResponse(
                400, "Bad Request", message, request.getRequestURI()));
    }

    @ExceptionHandler(DecodingException.class)
    public ResponseEntity<ErrorResponse> handleDecoding(
            DecodingException ex, HttpServletRequest request) {

        log.warn("Decoding error on {}: {}", request.getRequestURI(), ex.getMessage());

        return ResponseEntity.unprocessableContent().body(new ErrorResponse(
                422, "Unprocessable Entity", ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(PiZipException.class)
    public ResponseEntity<ErrorResponse> handlePiZip(
            PiZipException ex, HttpServletRequest request) {

        log.error("PiZip error on {}: {}", request.getRequestURI(), ex.getMessage(), ex);

        return ResponseEntity.internalServerError().body(new ErrorResponse(
                500, "Internal Server Error", ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(
            Exception ex, HttpServletRequest request) {

        log.error("Unexpected error on {}: {}", request.getRequestURI(), ex.getMessage(), ex);

        return ResponseEntity.internalServerError().body(new ErrorResponse(
                500, "Internal Server Error", "An unexpected error occurred", request.getRequestURI()));
    }
}
