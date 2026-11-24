package com.trilhamais.backend.service;

import com.trilhamais.backend.exception.ResourceNotFoundException;
import com.trilhamais.backend.model.*;
import com.trilhamais.backend.repository.*;
import jakarta.persistence.EntityManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * LGPD: exportacao de todos os dados do estudante e exclusao definitiva da conta.
 */
@Service
public class AccountDataService {

    private final UserRepository userRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final SubjectRepository subjectRepository;
    private final TopicRepository topicRepository;
    private final StudyMaterialRepository studyMaterialRepository;
    private final QuestionRepository questionRepository;
    private final ScheduleRepository scheduleRepository;
    private final QuizRepository quizRepository;
    private final QuizQuestionRepository quizQuestionRepository;
    private final QuizAnswerRepository quizAnswerRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final StudyActivityRepository studyActivityRepository;
    private final PasswordEncoder passwordEncoder;
    private final EntityManager entityManager;

    public AccountDataService(UserRepository userRepository,
                              UserSettingsRepository userSettingsRepository,
                              SubjectRepository subjectRepository,
                              TopicRepository topicRepository,
                              StudyMaterialRepository studyMaterialRepository,
                              QuestionRepository questionRepository,
                              ScheduleRepository scheduleRepository,
                              QuizRepository quizRepository,
                              QuizQuestionRepository quizQuestionRepository,
                              QuizAnswerRepository quizAnswerRepository,
                              ChatMessageRepository chatMessageRepository,
                              StudyActivityRepository studyActivityRepository,
                              PasswordEncoder passwordEncoder,
                              EntityManager entityManager) {
        this.userRepository = userRepository;
        this.userSettingsRepository = userSettingsRepository;
        this.subjectRepository = subjectRepository;
        this.topicRepository = topicRepository;
        this.studyMaterialRepository = studyMaterialRepository;
        this.questionRepository = questionRepository;
        this.scheduleRepository = scheduleRepository;
        this.quizRepository = quizRepository;
        this.quizQuestionRepository = quizQuestionRepository;
        this.quizAnswerRepository = quizAnswerRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.studyActivityRepository = studyActivityRepository;
        this.passwordEncoder = passwordEncoder;
        this.entityManager = entityManager;
    }

    /**
     * Monta um JSON com todos os dados do estudante (sem senha e sem hash da chave de recuperacao).
     */
    @Transactional(readOnly = true)
    public Map<String, Object> export(User principal) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado."));
        Long userId = user.getId();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("exportedAt", LocalDateTime.now());

        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("id", user.getId());
        profile.put("name", user.getName());
        profile.put("email", user.getEmail());
        profile.put("avatarEmoji", user.getAvatarEmoji());
        profile.put("streakDays", user.getStreakDays());
        profile.put("termsAcceptedAt", user.getTermsAcceptedAt());
        profile.put("termsVersion", user.getTermsVersion());
        profile.put("recoveryKeyCreatedAt", user.getRecoveryKeyCreatedAt());
        result.put("profile", profile);

        Map<String, Object> settings = new LinkedHashMap<>();
        UserSettings s = userSettingsRepository.findByUserId(userId).orElse(null);
        settings.put("notificationsEnabled", s == null || s.isNotificationsEnabled());
        settings.put("theme", s != null ? s.getTheme() : "light");
        result.put("settings", settings);

        List<Map<String, Object>> subjects = new ArrayList<>();
        for (Subject subject : subjectRepository.findByUserIdOrderByNameAsc(userId)) {
            Map<String, Object> sm = new LinkedHashMap<>();
            sm.put("id", subject.getId());
            sm.put("name", subject.getName());
            sm.put("description", subject.getDescription());
            sm.put("colorKey", subject.getColorKey());
            sm.put("primaryColor", subject.getPrimaryColor());
            sm.put("icon", subject.getIcon());
            sm.put("createdAt", subject.getCreatedAt());

            List<Map<String, Object>> topics = new ArrayList<>();
            for (Topic topic : topicRepository.findBySubjectId(subject.getId())) {
                Map<String, Object> tm = new LinkedHashMap<>();
                tm.put("id", topic.getId());
                tm.put("name", topic.getName());
                tm.put("description", topic.getDescription());
                tm.put("status", topic.getStatus());
                tm.put("createdAt", topic.getCreatedAt());

                List<Map<String, Object>> materials = new ArrayList<>();
                for (StudyMaterial m : studyMaterialRepository.findByTopicId(topic.getId())) {
                    Map<String, Object> mm = new LinkedHashMap<>();
                    mm.put("id", m.getId());
                    mm.put("title", m.getTitle());
                    mm.put("type", m.getType() != null ? m.getType().name() : null);
                    mm.put("content", m.getContent());
                    mm.put("createdAt", m.getCreatedAt());
                    materials.add(mm);
                }
                tm.put("materials", materials);

                List<Map<String, Object>> questions = new ArrayList<>();
                for (Question q : questionRepository.findByTopicId(topic.getId())) {
                    questions.add(questionMap(q));
                }
                tm.put("questions", questions);
                topics.add(tm);
            }
            sm.put("topics", topics);
            subjects.add(sm);
        }
        result.put("subjects", subjects);

        List<Map<String, Object>> schedules = new ArrayList<>();
        for (Schedule sc : scheduleRepository.findByUserOrderByDaysAheadAscScheduledTimeAsc(user)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", sc.getId());
            m.put("title", sc.getTitle());
            m.put("subjectName", sc.getSubjectName());
            m.put("subjectId", sc.getSubject() != null ? sc.getSubject().getId() : null);
            m.put("topicId", sc.getTopic() != null ? sc.getTopic().getId() : null);
            m.put("scheduledDate", sc.getScheduledDate());
            m.put("scheduledTime", sc.getScheduledTime());
            m.put("completed", sc.isCompleted());
            m.put("createdAt", sc.getCreatedAt());
            schedules.add(m);
        }
        result.put("schedules", schedules);

        List<Map<String, Object>> quizzes = new ArrayList<>();
        for (Quiz quiz : quizRepository.findByUserIdOrderByCreatedAtDesc(userId)) {
            Map<String, Object> qm = new LinkedHashMap<>();
            qm.put("id", quiz.getId());
            qm.put("title", quiz.getTitle());
            qm.put("subjectId", quiz.getSubject() != null ? quiz.getSubject().getId() : null);
            qm.put("topicId", quiz.getTopic() != null ? quiz.getTopic().getId() : null);
            qm.put("status", quiz.getStatus() != null ? quiz.getStatus().name() : null);
            qm.put("totalQuestions", quiz.getTotalQuestions());
            qm.put("correctAnswers", quiz.getCorrectAnswers());
            qm.put("score", quiz.getScore());
            qm.put("createdAt", quiz.getCreatedAt());
            qm.put("completedAt", quiz.getCompletedAt());

            List<Map<String, Object>> qqs = new ArrayList<>();
            for (QuizQuestion qq : quizQuestionRepository.findByQuizIdOrderByOrderIndexAsc(quiz.getId())) {
                Map<String, Object> m = questionMap(qq.getQuestion());
                m.put("order", qq.getOrderIndex());
                qqs.add(m);
            }
            qm.put("questions", qqs);

            List<Map<String, Object>> answers = new ArrayList<>();
            for (QuizAnswer a : quizAnswerRepository.findByQuizId(quiz.getId())) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("questionId", a.getQuestion() != null ? a.getQuestion().getId() : null);
                m.put("selectedOptionId", a.getSelectedOption() != null ? a.getSelectedOption().getId() : null);
                m.put("selectedOptionText", a.getSelectedOption() != null ? a.getSelectedOption().getText() : null);
                m.put("correct", a.isCorrect());
                answers.add(m);
            }
            qm.put("answers", answers);
            quizzes.add(qm);
        }
        result.put("quizzes", quizzes);

        List<Map<String, Object>> chat = new ArrayList<>();
        for (ChatMessage cm : chatMessageRepository.findByUserIdOrderByCreatedAtAscIdAsc(userId)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", cm.getId());
            m.put("subjectId", cm.getSubject() != null ? cm.getSubject().getId() : null);
            m.put("role", cm.getRole());
            m.put("content", cm.getContent());
            m.put("createdAt", cm.getCreatedAt());
            chat.add(m);
        }
        result.put("chatHistory", chat);

        List<Map<String, Object>> activities = new ArrayList<>();
        for (StudyActivity act : studyActivityRepository.findByUserIdOrderByActivityDateAsc(userId)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", act.getActivityDate());
            m.put("type", act.getActivityType() != null ? act.getActivityType().name() : null);
            activities.add(m);
        }
        result.put("activities", activities);

        return result;
    }

    /**
     * Apaga a conta e TODOS os dados ligados ao estudante, na ordem das chaves estrangeiras.
     * Senha errada -> 400 (IllegalArgumentException), igual a troca de senha.
     */
    @Transactional
    public void deleteAccount(User principal, String password) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado."));
        if (password == null || !passwordEncoder.matches(password, user.getPassword())) {
            throw new IllegalArgumentException("Senha incorreta.");
        }
        Long id = user.getId();

        // Subconsultas reutilizadas
        String userSubjects = "SELECT s.id FROM subjects s WHERE s.user_id = :uid";
        String userTopics = "SELECT t.id FROM topics t WHERE t.subject_id IN (" + userSubjects + ")";
        String userQuestions = "SELECT q.id FROM questions q WHERE q.topic_id IN (" + userTopics + ")";
        String userQuizzes = "SELECT z.id FROM quizzes z WHERE z.user_id = :uid"
                + " OR z.subject_id IN (" + userSubjects + ") OR z.topic_id IN (" + userTopics + ")";

        String[] statements = {
                // Simulados: respostas -> questoes do simulado -> simulados
                "DELETE FROM quiz_answers WHERE quiz_id IN (" + userQuizzes + ") OR question_id IN (" + userQuestions + ")"
                        + " OR selected_option_id IN (SELECT o.id FROM question_options o WHERE o.question_id IN (" + userQuestions + "))",
                "DELETE FROM quiz_questions WHERE quiz_id IN (" + userQuizzes + ") OR question_id IN (" + userQuestions + ")",
                "DELETE FROM quizzes WHERE id IN (" + userQuizzes + ")",
                // Banco de questoes: alternativas -> questoes
                "DELETE FROM question_options WHERE question_id IN (" + userQuestions + ")",
                "DELETE FROM questions WHERE id IN (" + userQuestions + ")",
                // Materiais, agenda e chat
                "DELETE FROM study_materials WHERE topic_id IN (" + userTopics + ")",
                "DELETE FROM schedules WHERE user_id = :uid OR subject_id IN (" + userSubjects + ") OR topic_id IN (" + userTopics + ")",
                "DELETE FROM chat_messages WHERE user_id = :uid OR subject_id IN (" + userSubjects + ")",
                // Assuntos -> materias
                "DELETE FROM topics WHERE id IN (" + userTopics + ")",
                "DELETE FROM subjects WHERE user_id = :uid",
                // Dados ligados direto ao usuario
                "DELETE FROM study_activities WHERE user_id = :uid",
                "DELETE FROM password_reset_tokens WHERE user_id = :uid",
                "DELETE FROM user_settings WHERE user_id = :uid",
                "DELETE FROM users WHERE id = :uid"
        };

        entityManager.flush();
        for (String sql : statements) {
            entityManager.createNativeQuery(sql).setParameter("uid", id).executeUpdate();
        }
        entityManager.clear();
    }

    private Map<String, Object> questionMap(Question q) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", q.getId());
        m.put("text", q.getText());
        m.put("explanation", q.getExplanation());
        m.put("source", q.getSource() != null ? q.getSource() : "MANUAL");
        List<Map<String, Object>> options = new ArrayList<>();
        for (QuestionOption o : q.getOptions()) {
            Map<String, Object> om = new LinkedHashMap<>();
            om.put("id", o.getId());
            om.put("text", o.getText());
            om.put("correct", o.isCorrect());
            options.add(om);
        }
        m.put("options", options);
        return m;
    }
}
