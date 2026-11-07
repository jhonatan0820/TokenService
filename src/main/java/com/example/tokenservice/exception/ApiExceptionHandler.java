package com.example.tokenservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ResponseStatusException.class)
    public org.springframework.http.ResponseEntity<Map<String, String>> handle(ResponseStatusException ex) {
        return org.springframework.http.ResponseEntity.status(ex.getStatusCode())
                .body(Map.of("error", ex.getReason() == null ? "Error" : ex.getReason()));
    }
    @ExceptionHandler(RestClientException.class)
    public org.springframework.http.ResponseEntity<Map<String, String>> cryptoUnavailable(RestClientException ex) {
        return org.springframework.http.ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("error", "No fue posible comunicarse con cryptoService"));
    }
}



