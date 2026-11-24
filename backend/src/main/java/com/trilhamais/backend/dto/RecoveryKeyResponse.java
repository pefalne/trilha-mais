package com.trilhamais.backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Resposta com a chave de recuperacao em texto (mostrada uma unica vez ao estudante).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RecoveryKeyResponse {

    private String message;
    private String recoveryKey;

    public RecoveryKeyResponse() {
    }

    public RecoveryKeyResponse(String recoveryKey) {
        this.recoveryKey = recoveryKey;
    }

    public RecoveryKeyResponse(String message, String recoveryKey) {
        this.message = message;
        this.recoveryKey = recoveryKey;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getRecoveryKey() {
        return recoveryKey;
    }

    public void setRecoveryKey(String recoveryKey) {
        this.recoveryKey = recoveryKey;
    }
}
