package com.trilhamais.backend.exception;

/**
 * Excecao lancada quando o limite de tentativas e atingido (429 Too Many Requests).
 */
public class TooManyRequestsException extends RuntimeException {
    public TooManyRequestsException(String message) {
        super(message);
    }
}
