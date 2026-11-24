package com.trilhamais.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trilhamais.backend.config.JwtService;
import com.trilhamais.backend.dto.QuizGenerateRequest;
import com.trilhamais.backend.model.*;
import com.trilhamais.backend.repository.*;
import com.trilhamais.backend.service.GroqAiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Bug do simulado: pedir 10 questoes deve devolver 10, usando a IA (aqui simulada) e,
 * se ela falhar, a contingencia com a quantidade pedida citando o assunto.
 */
@SpringBootTest
@AutoConfigureMockMvc
class QuizGenerationAiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private SubjectRepository subjectRepository;
    @Autowired private TopicRepository topicRepository;
    @Autowired private QuestionRepository questionRepository;
    @Autowired private QuizAnswerRepository quizAnswerRepository;
    @Autowired private QuizQuestionRepository quizQuestionRepository;
    @Autowired private QuizRepository quizRepository;
    @Autowired private ScheduleRepository scheduleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtService jwtService;

    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @MockitoBean private GroqAiService groqAiService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private String token;
    private Topic topic;

    @BeforeEach
    void setUp() {
        scheduleRepository.deleteAll();
        quizAnswerRepository.deleteAll();
        quizQuestionRepository.deleteAll();
        quizRepository.deleteAll();
        userRepository.deleteAll();

        User user = userRepository.save(new User("Aluno", "aluno.quiz@teste.com", passwordEncoder.encode("123456")));
        token = jwtService.generateToken(user);
        Subject subject = subjectRepository.save(new Subject("Biologia", "Seres vivos", user));
        topic = topicRepository.save(new Topic("Citologia", "Estudo das células", subject));
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        // Nao deixa dados que travem o deleteAll de usuarios em outras classes de teste
        scheduleRepository.deleteAll();
        quizAnswerRepository.deleteAll();
        quizQuestionRepository.deleteAll();
        quizRepository.deleteAll();
        userRepository.deleteAll();
    }

    private JsonNode generate(int count) throws Exception {
        String body = mockMvc.perform(post("/api/quizzes/generate")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new QuizGenerateRequest(topic.getId(), null, count, null))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    @Test
    @DisplayName("Com a IA respondendo, o simulado tem as 10 questões da IA com 4 alternativas e 1 correta")
    void shouldUseAiQuestions() throws Exception {
        List<GroqAiService.GeneratedQuestion> ai = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            ai.add(new GroqAiService.GeneratedQuestion("Pergunta de citologia " + i + "?",
                    List.of("Certa " + i, "Errada A" + i, "Errada B" + i, "Errada C" + i), 0, "Explicação " + i, "Citologia"));
        }
        when(groqAiService.generateQuizQuestions(eq("Biologia"), eq("Citologia"), any(), anyList(), eq(10))).thenReturn(ai);

        JsonNode quiz = generate(10);
        assertEquals(10, quiz.get("totalQuestions").asInt());
        assertEquals(10, quiz.get("questions").size());
        for (JsonNode q : quiz.get("questions")) {
            assertTrue(q.get("text").asText().startsWith("Pergunta de citologia"));
            assertEquals(4, q.get("options").size());
        }
        assertEquals(10, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM questions WHERE topic_id = ? AND source = 'AI'", Integer.class, topic.getId()));
        // Cada questao com 4 alternativas e exatamente 1 correta
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM questions q WHERE q.topic_id = ? AND ("
                        + "(SELECT COUNT(*) FROM question_options o WHERE o.question_id = q.id) <> 4 OR "
                        + "(SELECT COUNT(*) FROM question_options o WHERE o.question_id = q.id AND o.is_correct) <> 1)",
                Integer.class, topic.getId()));
    }

    @Test
    @DisplayName("Se a IA devolver só parte, completa com contingência até a quantidade pedida")
    void shouldCompleteWithFallback() throws Exception {
        when(groqAiService.generateQuizQuestions(any(), any(), any(), anyList(), anyInt())).thenReturn(List.of(
                new GroqAiService.GeneratedQuestion("Pergunta IA?", List.of("a", "b", "c", "d"), 2, null, null)));

        JsonNode quiz = generate(10);
        assertEquals(10, quiz.get("questions").size());
    }

    @Test
    @DisplayName("Sem IA: 10 questões de contingência, todas citando o assunto e sem as frases genéricas antigas")
    void shouldFallbackWithRequestedCount() throws Exception {
        when(groqAiService.generateQuizQuestions(any(), any(), any(), anyList(), anyInt())).thenReturn(List.of());

        JsonNode quiz = generate(10);
        assertEquals(10, quiz.get("totalQuestions").asInt());
        assertEquals(10, quiz.get("questions").size());
        java.util.Set<String> texts = new java.util.HashSet<>();
        for (JsonNode q : quiz.get("questions")) {
            String text = q.get("text").asText();
            assertTrue(text.contains("Citologia"), "Questão não cita o assunto: " + text);
            assertFalse(text.contains("Compreensão dos fundamentos"));
            assertEquals(4, q.get("options").size());
            texts.add(text);
        }
        assertEquals(10, texts.size(), "Questões repetidas");

        // As de contingência não são reaproveitadas no próximo simulado: a IA é chamada de novo para 10
        generate(10);
        org.mockito.Mockito.verify(groqAiService, org.mockito.Mockito.times(2))
                .generateQuizQuestions(any(), any(), any(), anyList(), eq(10));
    }

    @Test
    @DisplayName("Questões cadastradas pelo estudante entram primeiro; a IA só completa o restante")
    void shouldReuseManualQuestionsFirst() throws Exception {
        for (int i = 1; i <= 3; i++) {
            Question q = new Question("Manual " + i + "?", null, topic);
            q.addOption(new QuestionOption("Sim", true));
            q.addOption(new QuestionOption("Não", false));
            q.addOption(new QuestionOption("Talvez", false));
            q.addOption(new QuestionOption("Nunca", false));
            questionRepository.save(q);
        }
        when(groqAiService.generateQuizQuestions(any(), any(), any(), anyList(), eq(7))).thenReturn(List.of());

        JsonNode quiz = generate(10);
        assertEquals(10, quiz.get("questions").size());
        long manual = 0;
        for (JsonNode q : quiz.get("questions")) {
            if (q.get("text").asText().startsWith("Manual")) {
                manual++;
            }
        }
        assertEquals(3, manual);
    }
}
