package com.trilhamais.backend.controller;

import com.trilhamais.backend.dto.ChangePasswordRequest;
import com.trilhamais.backend.dto.MessageResponse;
import com.trilhamais.backend.dto.RegisterRequest;
import com.trilhamais.backend.dto.UpdateProfileRequest;
import com.trilhamais.backend.dto.UserResponse;
import com.trilhamais.backend.dto.UserSettingsDto;
import com.trilhamais.backend.model.User;
import com.trilhamais.backend.dto.DeleteAccountRequest;
import com.trilhamais.backend.dto.RecoveryKeyResponse;
import com.trilhamais.backend.service.AccountDataService;
import com.trilhamais.backend.service.RecoveryKeyService;
import com.trilhamais.backend.service.UserService;
import java.util.Map;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Controller REST responsavel pelas operacoes relacionadas a usuarios.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final RecoveryKeyService recoveryKeyService;
    private final AccountDataService accountDataService;

    public UserController(UserService userService, RecoveryKeyService recoveryKeyService,
                          AccountDataService accountDataService) {
        this.userService = userService;
        this.recoveryKeyService = recoveryKeyService;
        this.accountDataService = accountDataService;
    }

    /**
     * Endpoint para cadastro de um novo usuario.
     * POST /api/users/register
     */
    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        UserResponse response = userService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Endpoint protegido para obtencao do perfil do usuario autenticado com contadores de estudos.
     * GET /api/users/profile
     * Requer cabecalho Authorization: Bearer <token_jwt>
     */
    @GetMapping({"/profile", "/me"})
    public ResponseEntity<UserResponse> getProfile(@AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(userService.getProfile(user));
    }

    /**
     * Edita nome e avatar do estudante.
     * PUT /api/users/me
     */
    @PutMapping("/me")
    public ResponseEntity<UserResponse> updateProfile(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(userService.updateProfile(user, request));
    }

    /**
     * Troca a senha do estudante logado (exige a senha atual).
     * PUT /api/users/me/password
     */
    @PutMapping("/me/password")
    public ResponseEntity<MessageResponse> changePassword(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(user, request);
        return ResponseEntity.ok(new MessageResponse("Senha alterada com sucesso."));
    }

    /**
     * Configuracoes do Perfil (Notificacoes e Tema).
     * GET /api/users/me/settings
     */
    @GetMapping("/me/settings")
    public ResponseEntity<UserSettingsDto> getSettings(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(userService.getSettings(user));
    }

    /**
     * Atualiza as configuracoes do Perfil.
     * PUT /api/users/me/settings
     */
    @PutMapping("/me/settings")
    public ResponseEntity<UserSettingsDto> updateSettings(
            @AuthenticationPrincipal User user,
            @RequestBody UserSettingsDto request) {
        return ResponseEntity.ok(userService.updateSettings(user, request));
    }

    /**
     * Gera uma nova chave de recuperacao (a anterior deixa de valer). A chave aparece so nesta resposta.
     * POST /api/users/me/recovery-key
     */
    @PostMapping("/me/recovery-key")
    public ResponseEntity<RecoveryKeyResponse> regenerateRecoveryKey(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(recoveryKeyService.regenerate(user));
    }

    /**
     * LGPD: exporta todos os dados do estudante em JSON.
     * GET /api/users/me/export
     */
    @GetMapping("/me/export")
    public ResponseEntity<Map<String, Object>> exportData(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"trilha-mais-meus-dados.json\"")
                .body(accountDataService.export(user));
    }

    /**
     * LGPD: apaga a conta e todos os dados do estudante (exige a senha).
     * DELETE /api/users/me  body: { "password": "..." }
     */
    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteAccount(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody DeleteAccountRequest request) {
        accountDataService.deleteAccount(user, request.getPassword());
        return ResponseEntity.noContent().build();
    }
}
