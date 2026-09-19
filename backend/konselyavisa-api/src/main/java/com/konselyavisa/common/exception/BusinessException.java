package com.konselyavisa.common.exception;

import org.springframework.http.HttpStatus;

public class BusinessException extends RuntimeException {

    private final String messageKey;
    private final HttpStatus status;

    public BusinessException(String messageKey, HttpStatus status) {
        super(messageKey);
        this.messageKey = messageKey;
        this.status = status;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public static BusinessException notFound(String messageKey) {
        return new BusinessException(messageKey, HttpStatus.NOT_FOUND);
    }

    public static BusinessException forbidden(String messageKey) {
        return new BusinessException(messageKey, HttpStatus.FORBIDDEN);
    }

    public static BusinessException badRequest(String messageKey) {
        return new BusinessException(messageKey, HttpStatus.BAD_REQUEST);
    }

    public static BusinessException conflict(String messageKey) {
        return new BusinessException(messageKey, HttpStatus.CONFLICT);
    }

    public static BusinessException tooManyRequests(String messageKey) {
        return new BusinessException(messageKey, HttpStatus.TOO_MANY_REQUESTS);
    }
}
