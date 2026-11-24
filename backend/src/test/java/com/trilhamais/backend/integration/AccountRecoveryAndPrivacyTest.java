package com.trilhamais.backend.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trilhamais.backend.config.JwtService;
import com.trilhamais.backend.dto.*;
import com.trilhamais.backend.model.*;
import com.trilhamais.backend.repository.*;
import com.trilhamais.backend.service.RecoveryKeyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Chave de recuperacao (reset-password-key), aceite de termos, exportacao e exclusao de conta (LGPD).
 */
@SpringBootTest(properties = "groq.api.key=")
@AutoConfigureMockMvc
class AccountRecoveryAndPrivacyTest {

    private static final String KEY_PATTERN = "TRLH-[A-HJ-NP-Z2-9]{4}-[A-HJ-NP-Z2-9]{4}-[A-HJ-NP-Z2-9]{4}";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private ScheduleRepository scheduleRepository;
    @Autowired private QuizAnswerRepository quizAnswerRepository;
    @Autowired private QuizQuestionRepository quizQuestionRepository;
    @Autowired private QuizRepository quizRepository;
    @Autowired private ChatMessageRepository chatMessageRepository;
    @Autowired private PasswordResetTokenRepository tokenRepository;
    @Autowired private SubjectRepository subjectRepository;
    @Autowired private TopicRepository topicRepository;
    @Autowired private StudyMaterialRepository studyMaterialRepository;
    @Autowired private QuestionRepository questionRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtService jwtService;
    @Autowired private RecoveryKeyService recoveryKeyService;
    @Autowired private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        // Nao deixa agenda/chat/simulados que travem o deleteAll de usuarios em outras classes de teste
        setUp();
    }

    @BeforeEach
    void setUp() {
        scheduleRepository.deleteAll();
        quizAnswerRepository.deleteAll();
        quizQuestionRepository.deleteAll();
        quizRepository.deleteAll();
        chatMessageRepository.deleteAll();
        tokenRepository.deleteAll();
        userRepository.deleteAll();
        recoveryKeyService.clearAttempts();
    }

    private String register(String name, String email, String password) throws Exception {
        String body = mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(name, email, password, true))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.recoveryKey", matchesPattern(KEY_PATTERN)))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("recoveryKey").asText();
    }

    private org.springframework.test.web.servlet.ResultActions resetWithKey(String email, String key, String newPassword) throws Exception {
        return mockMvc.perform(post("/api/auth/reset-password-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ResetPasswordKeyRequest(email, key, newPassword))));
    }

    private void expectLogin(String email, String password, boolean ok) throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, password))))
                .andExpect(ok ? status().isOk() : status().isUnauthorized());
    }

    // ------------------------------------------------------------------ cadastro / termos

    @Test
    @DisplayName("Cadastro sem aceitar os termos deve ser recusado (400) e nada é salvo")
    void shouldRejectRegisterWithoutTerms() throws Exception {
        mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ana\",\"email\":\"ana@teste.com\",\"password\":\"123456\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("É preciso aceitar os Termos de Uso e a Política de Privacidade.")));

        mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ana\",\"email\":\"ana@teste.com\",\"password\":\"123456\",\"acceptedTerms\":false}"))
                .andExpect(status().isBadRequest());

        assertEquals(0, userRepository.count());
    }

    @Test
    @DisplayName("Cadastro salva o aceite dos termos (versão padrão) e só o hash da chave")
    void shouldSaveTermsAndHashedKey() throws Exception {
        String key = register("Ana", "ana@teste.com", "123456");

        User user = userRepository.findByEmail("ana@teste.com").orElseThrow();
        assertNotNull(user.getTermsAcceptedAt());
        assertEquals("2026-11-24", user.getTermsVersion());
        assertNotNull(user.getRecoveryKeyCreatedAt());
        assertNotEquals(key, user.getRecoveryKeyHash());
        assertTrue(passwordEncoder.matches(key.replace("-", ""), user.getRecoveryKeyHash()));
    }

    // ------------------------------------------------------------------ reset-password-key

    @Test
    @DisplayName("Reset com chave válida (minúsculas e espaços) troca a senha, devolve nova chave e a antiga morre")
    void shouldResetPasswordWithKey() throws Exception {
        String key = register("Bia", "bia@teste.com", "senhaAntiga");
        String messy = "  " + key.toLowerCase().replace("-", " - ") + " ";

        String body = resetWithKey("BIA@teste.com", messy, "novaSenha1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", notNullValue()))
                .andExpect(jsonPath("$.recoveryKey", matchesPattern(KEY_PATTERN)))
                .andReturn().getResponse().getContentAsString();
        String newKey = objectMapper.readTree(body).get("recoveryKey").asText();
        assertNotEquals(key, newKey);

        expectLogin("bia@teste.com", "novaSenha1", true);
        expectLogin("bia@teste.com", "senhaAntiga", false);

        // Chave antiga nao vale mais; a nova vale
        resetWithKey("bia@teste.com", key, "outraSenha1").andExpect(status().isBadRequest());
        resetWithKey("bia@teste.com", newKey, "outraSenha1").andExpect(status().isOk());
    }

    @Test
    @DisplayName("Chave errada ou e-mail inexistente: 400 com a mesma mensagem genérica e senha mantida")
    void shouldRejectWrongKey() throws Exception {
        register("Caio", "caio@teste.com", "senhaAntiga");

        resetWithKey("caio@teste.com", "TRLH-AAAA-BBBB-CCCC", "novaSenha1")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("E-mail ou chave de recuperação inválidos.")));
        resetWithKey("naoexiste@teste.com", "TRLH-AAAA-BBBB-CCCC", "novaSenha1")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("E-mail ou chave de recuperação inválidos.")));

        expectLogin("caio@teste.com", "senhaAntiga", true);
    }

    @Test
    @DisplayName("Nova senha curta é recusada pela validação (400)")
    void shouldValidateNewPassword() throws Exception {
        String key = register("Duda", "duda@teste.com", "senhaAntiga");
        resetWithKey("duda@teste.com", key, "123").andExpect(status().isBadRequest());
        expectLogin("duda@teste.com", "senhaAntiga", true);
    }

    @Test
    @DisplayName("5 falhas no mesmo e-mail em 15 min bloqueiam com 429, até com a chave certa")
    void shouldRateLimitAfterFiveFailures() throws Exception {
        String key = register("Eva", "eva@teste.com", "senhaAntiga");

        for (int i = 0; i < 5; i++) {
            resetWithKey("eva@teste.com", "TRLH-AAAA-BBBB-CCCC", "novaSenha1").andExpect(status().isBadRequest());
        }
        resetWithKey("eva@teste.com", key, "novaSenha1")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message", is("Muitas tentativas. Tente de novo em 15 minutos.")));
        expectLogin("eva@teste.com", "senhaAntiga", true);

        // Outro e-mail nao e afetado
        String otherKey = register("Fabi", "fabi@teste.com", "senhaAntiga");
        resetWithKey("fabi@teste.com", otherKey, "novaSenha1").andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/users/me/recovery-key gera nova chave e invalida a anterior; sem token = 401")
    void shouldRegenerateRecoveryKey() throws Exception {
        String oldKey = register("Gil", "gil@teste.com", "senhaAntiga");
        User user = userRepository.findByEmail("gil@teste.com").orElseThrow();
        String token = jwtService.generateToken(user);

        mockMvc.perform(post("/api/users/me/recovery-key")).andExpect(status().isUnauthorized());

        String body = mockMvc.perform(post("/api/users/me/recovery-key").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recoveryKey", matchesPattern(KEY_PATTERN)))
                .andExpect(jsonPath("$.message").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        String newKey = objectMapper.readTree(body).get("recoveryKey").asText();

        resetWithKey("gil@teste.com", oldKey, "novaSenha1").andExpect(status().isBadRequest());
        resetWithKey("gil@teste.com", newKey, "novaSenha1").andExpect(status().isOk());
    }

    @Test
    @DisplayName("Perfil (GET /me) não expõe a chave nem o hash")
    void profileShouldNotExposeKey() throws Exception {
        register("Hugo", "hugo@teste.com", "senhaAntiga");
        User user = userRepository.findByEmail("hugo@teste.com").orElseThrow();
        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + jwtService.generateToken(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recoveryKey").doesNotExist())
                .andExpect(jsonPath("$.recoveryKeyHash").doesNotExist());
    }

    // ------------------------------------------------------------------ export / delete

    /** Cria um estudante com dados em todas as tabelas e devolve o token. */
    private String createUserWithAllData(String email) throws Exception {
        register("Estudante", email, "minhaSenha1");
        User user = userRepository.findByEmail(email).orElseThrow();
        String token = jwtService.generateToken(user);

        Subject subject = subjectRepository.save(new Subject("Biologia", "Seres vivos", user));
        Topic topic = topicRepository.save(new Topic("Citologia", "Estudo das células", subject));
        studyMaterialRepository.save(new StudyMaterial("Resumo de células", "A célula é a unidade da vida.", MaterialType.SUMMARY, topic));
        Question q = new Question("Qual organela produz energia?", "A mitocôndria faz a respiração celular.", topic);
        q.addOption(new QuestionOption("Mitocôndria", true));
        q.addOption(new QuestionOption("Ribossomo", false));
        q.addOption(new QuestionOption("Lisossomo", false));
        q.addOption(new QuestionOption("Complexo golgiense", false));
        questionRepository.save(q);

        Schedule schedule = new Schedule("Revisar citologia", "Biologia", "biologia", "🧬", LocalDate.now(), 0, "14:00", user);
        scheduleRepository.save(schedule);
        chatMessageRepository.save(new ChatMessage(user, subject, "user", "O que é mitocôndria?"));
        chatMessageRepository.save(new ChatMessage(user, subject, "assistant", "É a organela da respiração celular."));
        tokenRepository.save(new PasswordResetToken(user, "0".repeat(64), LocalDateTime.now().plusMinutes(30)));

        mockMvc.perform(put("/api/users/me/settings").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"theme\":\"dark\"}"))
                .andExpect(status().isOk());

        // Simulado gerado e respondido (gera quiz_questions, quiz_answers e atividade do dia)
        String quizBody = mockMvc.perform(post("/api/quizzes/generate").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new QuizGenerateRequest(topic.getId(), null, 3, null))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode quiz = objectMapper.readTree(quizBody);
        List<AnswerItemRequest> answers = new ArrayList<>();
        for (JsonNode question : quiz.get("questions")) {
            answers.add(new AnswerItemRequest(question.get("id").asLong(), question.get("options").get(0).get("id").asLong()));
        }
        mockMvc.perform(post("/api/quizzes/" + quiz.get("id").asLong() + "/submit").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new QuizSubmitRequest(answers))))
                .andExpect(status().isOk());
        return token;
    }

    @Test
    @DisplayName("GET /api/users/me/export devolve todos os dados do estudante, sem senha nem hash")
    void shouldExportAllData() throws Exception {
        String token = createUserWithAllData("export@teste.com");

        String body = mockMvc.perform(get("/api/users/me/export").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.email", is("export@teste.com")))
                .andExpect(jsonPath("$.profile.termsVersion", is("2026-11-24")))
                .andExpect(jsonPath("$.settings.theme", is("dark")))
                .andExpect(jsonPath("$.subjects", hasSize(1)))
                .andExpect(jsonPath("$.subjects[0].topics[0].name", is("Citologia")))
                .andExpect(jsonPath("$.subjects[0].topics[0].materials", hasSize(1)))
                .andExpect(jsonPath("$.subjects[0].topics[0].questions", not(empty())))
                .andExpect(jsonPath("$.schedules", hasSize(1)))
                .andExpect(jsonPath("$.quizzes", hasSize(1)))
                .andExpect(jsonPath("$.quizzes[0].questions", hasSize(3)))
                .andExpect(jsonPath("$.quizzes[0].answers", hasSize(3)))
                .andExpect(jsonPath("$.chatHistory", hasSize(2)))
                .andExpect(jsonPath("$.activities", not(empty())))
                .andReturn().getResponse().getContentAsString();

        assertFalse(body.contains("\"password\""));
        assertFalse(body.contains("recoveryKeyHash"));
        assertFalse(body.contains("$2a$"), "Nenhum hash BCrypt pode sair no export");

        mockMvc.perform(get("/api/users/me/export")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("DELETE /api/users/me com senha errada: 400 e nada é apagado")
    void shouldNotDeleteWithWrongPassword() throws Exception {
        String token = createUserWithAllData("fica@teste.com");

        mockMvc.perform(delete("/api/users/me").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DeleteAccountRequest("senhaErrada"))))
                .andExpect(status().isBadRequest());

        assertTrue(userRepository.findByEmail("fica@teste.com").isPresent());
        assertEquals(1, quizRepository.count());
    }

    @Test
    @DisplayName("DELETE /api/users/me apaga o usuário e TODOS os dados ligados, sem mexer em outro estudante")
    void shouldDeleteAccountAndAllData() throws Exception {
        String otherToken = createUserWithAllData("outro@teste.com");
        Long otherId = userRepository.findByEmail("outro@teste.com").orElseThrow().getId();
        String token = createUserWithAllData("sai@teste.com");
        Long userId = userRepository.findByEmail("sai@teste.com").orElseThrow().getId();

        mockMvc.perform(delete("/api/users/me").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DeleteAccountRequest("minhaSenha1"))))
                .andExpect(status().isNoContent());

        assertTrue(userRepository.findById(userId).isEmpty());
        Map<String, String> checks = Map.of(
                "users", "SELECT COUNT(*) FROM users WHERE id = ?",
                "user_settings", "SELECT COUNT(*) FROM user_settings WHERE user_id = ?",
                "subjects", "SELECT COUNT(*) FROM subjects WHERE user_id = ?",
                "topics", "SELECT COUNT(*) FROM topics t JOIN subjects s ON s.id = t.subject_id WHERE s.user_id = ?",
                "quizzes", "SELECT COUNT(*) FROM quizzes WHERE user_id = ?",
                "schedules", "SELECT COUNT(*) FROM schedules WHERE user_id = ?",
                "chat_messages", "SELECT COUNT(*) FROM chat_messages WHERE user_id = ?",
                "study_activities", "SELECT COUNT(*) FROM study_activities WHERE user_id = ?",
                "password_reset_tokens", "SELECT COUNT(*) FROM password_reset_tokens WHERE user_id = ?");
        checks.forEach((table, sql) ->
                assertEquals(0, jdbcTemplate.queryForObject(sql, Integer.class, userId), "Sobrou dado em " + table));

        // O outro estudante continua com tudo
        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM quizzes WHERE user_id = ?", Integer.class, otherId));
        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM subjects WHERE user_id = ?", Integer.class, otherId));
        assertEquals(2, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM chat_messages WHERE user_id = ?", Integer.class, otherId));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM study_materials m JOIN topics t ON t.id = m.topic_id JOIN subjects s ON s.id = t.subject_id WHERE s.user_id = ?",
                Integer.class, otherId));
        mockMvc.perform(get("/api/users/me/export").header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk());

        // Token do usuario apagado deixa de funcionar
        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
        // E-mail fica livre para novo cadastro
        register("Estudante", "sai@teste.com", "novaSenha1");
    }
}
