package com.trilhamais.backend.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * DTO da exclusao de conta: exige a senha atual para confirmar.
 */
public class DeleteAccountRequest {

    @NotBlank(message = "A senha é obrigatória.")
    private String password;

    public DeleteAccountRequest() {
    }

    public DeleteAccountRequest(String password) {
        this.password = password;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
