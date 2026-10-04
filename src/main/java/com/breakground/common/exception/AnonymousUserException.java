package com.breakground.common.exception;

import com.breakground.anonymoususer.AnonymousUserController;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice(assignableTypes = AnonymousUserController.class)
public class AnonymousUserException {

    @ExceptionHandler(value = MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationException
        (MethodArgumentNotValidException e) {

        String message = e.getBindingResult()
            .getFieldError()
            .getDefaultMessage();

        return ResponseEntity.badRequest()
            .body(Map.of("message", message));
    }
}
