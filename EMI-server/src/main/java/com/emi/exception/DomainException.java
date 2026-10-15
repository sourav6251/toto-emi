package com.emi.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Business-rule violation. Responds with 400 Bad Request unless another status is given.
 */
@Getter
public class DomainException extends RuntimeException {

    private final HttpStatus status;

    public DomainException(String message) {
        this(message, HttpStatus.BAD_REQUEST);
    }

    public DomainException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }
}
