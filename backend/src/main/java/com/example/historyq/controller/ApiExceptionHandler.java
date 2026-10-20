package com.example.historyq.controller;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.NoSuchElementException;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger logger =
            LoggerFactory.getLogger(ApiExceptionHandler.class);

    public record ApiError(String message) {
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(
            AccessDeniedException exception
    ) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiError(exception.getMessage()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handleResponseStatus(
            ResponseStatusException exception
    ) {
        return ResponseEntity.status(exception.getStatusCode())
                .body(new ApiError(exception.getReason()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(
            MethodArgumentNotValidException exception
    ) {
        String message = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("I dati inviati non sono validi.");
        return ResponseEntity.badRequest().body(new ApiError(message));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(
            ConstraintViolationException exception
    ) {
        return ResponseEntity.badRequest()
                .body(new ApiError("I dati inviati non sono validi."));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleConflict(
            IllegalArgumentException exception
    ) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError(exception.getMessage()));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ApiError> handleNotFound(
            NoSuchElementException exception
    ) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError(exception.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception exception) {
        logger.error("Errore inatteso durante la richiesta API", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError("Si è verificato un errore interno."));
    }
}
