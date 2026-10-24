package com.emi.exception;

import com.emi.dto.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiResponse> handleDomain(DomainException ex, HttpServletRequest request) {
        HttpStatus status = ex.getStatus().is4xxClientError() ? ex.getStatus() : HttpStatus.BAD_REQUEST;
        return build(status, ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }

        HttpStatus status = HttpStatus.BAD_REQUEST;
        ApiResponse body = ApiResponse.error("Validation failed")
                .add("status", status.value())
                .add("error", status.getReasonPhrase())
                .add("path", request.getRequestURI())
                .add("timestamp", OffsetDateTime.now())
                .add("fieldErrors", fieldErrors);
        return ResponseEntity.status(status).body(body);
    }

    @ExceptionHandler(HttpMessageConversionException.class)
    public ResponseEntity<ApiResponse> handleConversion(HttpMessageConversionException ex, HttpServletRequest request) {
        log.warn("Request body conversion error on {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, "Malformed request body or type error", request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        log.warn("HTTP method not supported: {} {}", request.getMethod(), request.getRequestURI());
        return build(HttpStatus.METHOD_NOT_ALLOWED, ex.getMessage(), request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        log.warn("Media type not supported on {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ex.getMessage(), request);
    }

    // Missing or wrongly-typed @RequestParam / @PathVariable (e.g. an unknown enum value)
    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiResponse> handleBadParameter(Exception ex, HttpServletRequest request) {
        String message = ex instanceof MethodArgumentTypeMismatchException mismatch
                ? "Invalid value '" + mismatch.getValue() + "' for parameter '" + mismatch.getName() + "'"
                : ex.getMessage();
        return build(HttpStatus.BAD_REQUEST, message, request);
    }

    // Multipart limit from spring.servlet.multipart.* (document photos are capped at 5 MB)
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse> handleUploadTooLarge(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        return build(HttpStatus.CONTENT_TOO_LARGE, "File is larger than 5 MB", request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, "You do not have permission to perform this action", request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse> handleNotFound(NoResourceFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong", request);
    }

    private ResponseEntity<ApiResponse> build(HttpStatus status, String message, HttpServletRequest request) {
        ApiResponse body = ApiResponse.error(message)
                .add("status", status.value())
                .add("error", status.getReasonPhrase())
                .add("path", request.getRequestURI())
                .add("timestamp", OffsetDateTime.now());
        return ResponseEntity.status(status).body(body);
    }
}
