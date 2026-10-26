package com.petsitting;

import java.util.Map;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiErrors {
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<Map<String,Object>> business(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode()).body(Map.of("status", ex.getStatusCode().value(), "message", ex.getReason() == null ? "请求失败" : ex.getReason()));
    }
    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class, HandlerMethodValidationException.class, org.springframework.http.converter.HttpMessageNotReadableException.class, org.springframework.web.multipart.MaxUploadSizeExceededException.class})
    ResponseEntity<Map<String,Object>> validation(Exception ex) {
        return ResponseEntity.badRequest().body(Map.of("status", 400, "message", "请求参数校验失败"));
    }
}
