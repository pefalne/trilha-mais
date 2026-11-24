package com.trilhamais.backend.service;

import com.trilhamais.backend.dto.RecoveryKeyResponse;
import com.trilhamais.backend.dto.ResetPasswordKeyRequest;
import com.trilhamais.backend.exception.ResourceNotFoundException;
import com.trilhamais.backend.exception.TooManyRequestsException;
import com.trilhamais.backend.model.User;
import com.trilhamais.backend.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Recuperacao de senha por CHAVE DE RECUPERACAO (sem e-mail).
 * - A chave tem o formato TRLH-XXXX-XXXX-XXXX (letras A-Z sem I/O e numeros 2-9), gerada com SecureRandom.
 * - E mostrada ao estudante uma unica vez (no cadastro ou ao gerar uma nova); no banco fica so o hash BCrypt.
 * - A comparacao ignora maiusculas/minusculas, espacos e hifens.
 * - Depois de usar a chave para trocar a senha, uma NOVA chave e gerada e a antiga deixa de valer.
 * - Limite em memoria: 5 falhas por e-mail em 15 minutos -> 429.
 */
@Service
public class RecoveryKeyService {

    static final String PREFIX = "TRLH";
    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int MAX_FAILURES = 5;
    private static final Duration WINDOW = Duration.ofMinutes(15);
    static final String INVALID_MESSAGE = "E-mail ou chave de recuperação inválidos.";
    static final String TOO_MANY_MESSAGE = "Muitas tentativas. Tente de novo em 15 minutos.";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();
    // Hash qualquer usado quando o e-mail nao existe, para o tempo de resposta ser parecido
    private final String dummyHash;

    public RecoveryKeyService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.dummyHash = passwordEncoder.encode("TRLHDUMMYKEY0000");
    }

    /**
     * Gera uma nova chave, grava o hash no usuario (sem salvar) e devolve a chave em texto.
     */
    public String issueNewKey(User user) {
        String key = generateKey();
        user.setRecoveryKeyHash(passwordEncoder.encode(canonical(key)));
        user.setRecoveryKeyCreatedAt(LocalDateTime.now());
        return key;
    }

    /**
     * Gera nova chave para o estudante logado; a anterior deixa de valer.
     */
    @Transactional
    public RecoveryKeyResponse regenerate(User principal) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado."));
        String key = issueNewKey(user);
        userRepository.save(user);
        return new RecoveryKeyResponse(key);
    }

    /**
     * Troca a senha usando e-mail + chave. Em caso de sucesso devolve uma nova chave.
     */
    @Transactional
    public RecoveryKeyResponse resetPassword(ResetPasswordKeyRequest request) {
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        checkRateLimit(email);

        Optional<User> userOpt = userRepository.findByEmail(email);
        String rawKey = canonical(request.getRecoveryKey());
        boolean ok;
        if (userOpt.isPresent() && userOpt.get().getRecoveryKeyHash() != null) {
            ok = passwordEncoder.matches(rawKey, userOpt.get().getRecoveryKeyHash());
        } else {
            passwordEncoder.matches(rawKey, dummyHash);
            ok = false;
        }

        if (!ok) {
            registerFailure(email);
            throw new IllegalArgumentException(INVALID_MESSAGE);
        }

        failures.remove(email);
        User user = userOpt.get();
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        String newKey = issueNewKey(user);
        userRepository.save(user);
        return new RecoveryKeyResponse(
                "Senha alterada com sucesso. Guarde a nova chave de recuperação: a anterior não vale mais.",
                newKey);
    }

    /** Limpa o contador de tentativas (usado nos testes). */
    public void clearAttempts() {
        failures.clear();
    }

    static String canonical(String key) {
        if (key == null) {
            return "";
        }
        return key.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
    }

    private String generateKey() {
        StringBuilder sb = new StringBuilder(PREFIX);
        for (int group = 0; group < 3; group++) {
            sb.append('-');
            for (int i = 0; i < 4; i++) {
                sb.append(ALPHABET[random.nextInt(ALPHABET.length)]);
            }
        }
        return sb.toString();
    }

    private void checkRateLimit(String email) {
        Deque<Instant> deque = failures.get(email);
        if (deque == null) {
            return;
        }
        synchronized (deque) {
            prune(deque);
            if (deque.size() >= MAX_FAILURES) {
                throw new TooManyRequestsException(TOO_MANY_MESSAGE);
            }
        }
    }

    private void registerFailure(String email) {
        Deque<Instant> deque = failures.computeIfAbsent(email, k -> new ArrayDeque<>());
        synchronized (deque) {
            prune(deque);
            deque.addLast(Instant.now());
        }
        // Evita crescer sem limite: remove e-mails sem falhas recentes
        if (failures.size() > 10_000) {
            failures.entrySet().removeIf(e -> {
                synchronized (e.getValue()) {
                    prune(e.getValue());
                    return e.getValue().isEmpty();
                }
            });
        }
    }

    private static void prune(Deque<Instant> deque) {
        Instant limit = Instant.now().minus(WINDOW);
        while (!deque.isEmpty() && deque.peekFirst().isBefore(limit)) {
            deque.pollFirst();
        }
    }
}
