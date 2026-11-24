package com.trilhamais.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO da troca de senha usando a chave de recuperacao (TRLH-XXXX-XXXX-XXXX).
 */
public class ResetPasswordKeyRequest {

    @NotBlank(message = "O e-mail é obrigatório.")
    @Email(message = "Formato de e-mail inválido.")
    private String email;

    @NotBlank(message = "A chave de recuperação é obrigatória.")
    private String recoveryKey;

    @NotBlank(message = "A nova senha é obrigatória.")
    @Size(min = 6, message = "A senha deve conter no mínimo 6 caracteres.")
    private String newPassword;

    public ResetPasswordKeyRequest() {
    }

    public ResetPasswordKeyRequest(String email, String recoveryKey, String newPassword) {
        this.email = email;
        this.recoveryKey = recoveryKey;
        this.newPassword = newPassword;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getRecoveryKey() {
        return recoveryKey;
    }

    public void setRecoveryKey(String recoveryKey) {
        this.recoveryKey = recoveryKey;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }
}
