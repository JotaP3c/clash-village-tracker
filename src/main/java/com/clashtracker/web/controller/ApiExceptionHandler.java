package com.clashtracker.web.controller;

import com.clashtracker.importer.InputDataException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/** Translates domain input failures into a structured HTTP response without leaking stack traces. */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(InputDataException.class)
    public ResponseEntity<Map<String, Object>> handleInputData(InputDataException exception) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                "error", "INPUT_DATA_ERROR",
                "message", exception.getMessage() == null ? "Invalid input data" : exception.getMessage()));
    }
}
