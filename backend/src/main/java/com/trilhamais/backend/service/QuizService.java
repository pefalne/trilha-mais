package com.trilhamais.backend.service;

import com.trilhamais.backend.dto.*;
import com.trilhamais.backend.exception.ResourceNotFoundException;
import com.trilhamais.backend.model.*;
import com.trilhamais.backend.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class QuizService {

    private final QuizRepository quizRepository;
    private final QuizQuestionRepository quizQuestionRepository;
    private final QuizAnswerRepository quizAnswerRepository;
    private final QuestionRepository questionRepository;
    private final QuestionOptionRepository questionOptionRepository;
    private final TopicRepository topicRepository;
    private final SubjectRepository subjectRepository;
    private final QuestionService questionService;
    private final StudyActivityService studyActivityService;
    private final GroqAiService groqAiService;
    private final TransactionTemplate transactionTemplate;

    public QuizService(QuizRepository quizRepository,
                       QuizQuestionRepository quizQuestionRepository,
                       QuizAnswerRepository quizAnswerRepository,
                       QuestionRepository questionRepository,
                       QuestionOptionRepository questionOptionRepository,
                       TopicRepository topicRepository,
                       SubjectRepository subjectRepository,
                       QuestionService questionService,
                       StudyActivityService studyActivityService,
                       GroqAiService groqAiService,
                       PlatformTransactionManager transactionManager) {
        this.quizRepository = quizRepository;
        this.quizQuestionRepository = quizQuestionRepository;
        this.quizAnswerRepository = quizAnswerRepository;
        this.questionRepository = questionRepository;
        this.questionOptionRepository = questionOptionRepository;
        this.topicRepository = topicRepository;
        this.subjectRepository = subjectRepository;
        this.questionService = questionService;
        this.studyActivityService = studyActivityService;
        this.groqAiService = groqAiService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * Gera um simulado com a quantidade pedida de questoes.
     * 1) Reaproveita questoes cadastradas pelo estudante no assunto/materia (sem as genericas antigas).
     * 2) Completa o que faltar com questoes geradas pela IA (Groq) sobre a materia/assunto.
     * 3) Se a IA falhar, completa com questoes de revisao que citam o assunto.
     * A chamada a IA acontece fora da transacao, para nao segurar conexao do banco.
     */
    public QuizDetailResponse generateQuiz(User user, QuizGenerateRequest request) {
        GenerationPlan plan = transactionTemplate.execute(status -> buildPlan(user, request));

        int missing = plan.desiredCount() - plan.reuseQuestionIds().size();
        List<GroqAiService.GeneratedQuestion> aiQuestions = missing > 0
                ? groqAiService.generateQuizQuestions(plan.subjectName(), plan.topicName(), plan.topicDescription(),
                        plan.topicNames(), missing)
                : List.of();

        return transactionTemplate.execute(status -> persistQuiz(user, request, plan, aiQuestions));
    }

    private record GenerationPlan(Long topicId, Long subjectId, String subjectName, String topicName,
                                  String topicDescription, List<Long> topicIds, List<String> topicNames,
                                  List<Long> reuseQuestionIds, int desiredCount) {
    }

    private GenerationPlan buildPlan(User user, QuizGenerateRequest request) {
        Topic topic = null;
        Subject subject;
        List<Question> candidateQuestions;

        if (request.getTopicId() != null) {
            topic = topicRepository.findByIdAndSubjectUserId(request.getTopicId(), user.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Tópico não encontrado com ID: " + request.getTopicId()));
            subject = topic.getSubject();
            candidateQuestions = questionRepository.findByTopicIdAndTopicSubjectUserId(topic.getId(), user.getId());
        } else if (request.getSubjectId() != null) {
            subject = subjectRepository.findByIdAndUserId(request.getSubjectId(), user.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Matéria não encontrada com ID: " + request.getSubjectId()));
            candidateQuestions = questionRepository.findByTopicSubjectIdAndTopicSubjectUserId(subject.getId(), user.getId());
        } else {
            throw new IllegalArgumentException("É necessário informar ao menos um topicId ou subjectId para gerar o simulado.");
        }

        int desiredCount = (request.getQuestionCount() != null && request.getQuestionCount() > 0)
                ? Math.min(request.getQuestionCount(), 50)
                : 10;

        // So reaproveita questoes cadastradas pelo estudante (nao as geradas pela IA/contingencia nem as genericas antigas)
        List<Question> reusable = new ArrayList<>(candidateQuestions.stream()
                .filter(q -> q.getSource() == null || "MANUAL".equals(q.getSource()))
                .filter(q -> !isLegacyGenericQuestion(q))
                .filter(q -> q.getOptions().stream().anyMatch(QuestionOption::isCorrect))
                .toList());
        Collections.shuffle(reusable);
        List<Long> reuseIds = reusable.stream().limit(desiredCount).map(Question::getId).toList();

        List<Topic> targetTopics;
        if (topic != null) {
            targetTopics = List.of(topic);
        } else {
            targetTopics = topicRepository.findBySubjectIdAndSubjectUserIdOrderByNameAsc(subject.getId(), user.getId());
            if (targetTopics.isEmpty() && reuseIds.size() < desiredCount) {
                // Questao precisa de um assunto: cria um assunto geral na materia
                targetTopics = List.of(topicRepository.save(new Topic("Introdução Geral", "Conteúdo introdutório da matéria", subject)));
            }
        }

        return new GenerationPlan(
                topic != null ? topic.getId() : null,
                subject.getId(),
                subject.getName(),
                topic != null ? topic.getName() : null,
                topic != null ? topic.getDescription() : null,
                targetTopics.stream().map(Topic::getId).toList(),
                targetTopics.stream().map(Topic::getName).toList(),
                reuseIds,
                desiredCount);
    }

    private QuizDetailResponse persistQuiz(User user, QuizGenerateRequest request, GenerationPlan plan,
                                           List<GroqAiService.GeneratedQuestion> aiQuestions) {
        Subject subject = subjectRepository.findByIdAndUserId(plan.subjectId(), user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Matéria não encontrada com ID: " + plan.subjectId()));
        Topic topic = plan.topicId() != null
                ? topicRepository.findByIdAndSubjectUserId(plan.topicId(), user.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Tópico não encontrado com ID: " + plan.topicId()))
                : null;
        List<Topic> targetTopics = new ArrayList<>(topicRepository.findAllById(plan.topicIds()));
        targetTopics.sort(Comparator.comparing(Topic::getName, String.CASE_INSENSITIVE_ORDER));

        List<Question> selectedQuestions = new ArrayList<>(questionRepository.findAllById(plan.reuseQuestionIds()));

        int index = 0;
        for (GroqAiService.GeneratedQuestion generated : aiQuestions) {
            if (selectedQuestions.size() >= plan.desiredCount() || targetTopics.isEmpty()) {
                break;
            }
            Topic target = matchTopic(targetTopics, generated.topic(), index++);
            Question q = new Question(generated.text(), generated.explanation(), target);
            q.setSource("AI");
            List<QuestionOption> options = new ArrayList<>();
            for (int i = 0; i < generated.options().size(); i++) {
                options.add(new QuestionOption(generated.options().get(i), i == generated.correctIndex()));
            }
            Collections.shuffle(options);
            options.forEach(q::addOption);
            selectedQuestions.add(questionRepository.save(q));
        }

        int missing = plan.desiredCount() - selectedQuestions.size();
        if (missing > 0 && !targetTopics.isEmpty()) {
            selectedQuestions.addAll(createFallbackQuestions(subject, targetTopics, missing));
        }

        Collections.shuffle(selectedQuestions);
        int actualCount = selectedQuestions.size();

        String title = (request.getTitle() != null && !request.getTitle().isBlank())
                ? request.getTitle().trim()
                : "Simulado: " + (topic != null ? topic.getName() : subject.getName()) + " (" + actualCount + " questões)";

        Quiz quiz = new Quiz(title, user, topic, subject, actualCount);
        Quiz savedQuiz = quizRepository.save(quiz);

        int order = 1;
        for (Question q : selectedQuestions) {
            QuizQuestion qq = new QuizQuestion(savedQuiz, q, order++);
            savedQuiz.addQuizQuestion(qq);
            quizQuestionRepository.save(qq);
        }

        return mapToDetailResponse(savedQuiz);
    }

    private static Topic matchTopic(List<Topic> topics, String topicName, int index) {
        if (topicName != null && !topicName.isBlank()) {
            String wanted = topicName.trim().toLowerCase(Locale.ROOT);
            for (Topic t : topics) {
                if (t.getName() != null && t.getName().trim().toLowerCase(Locale.ROOT).equals(wanted)) {
                    return t;
                }
            }
        }
        return topics.get(index % topics.size());
    }

    // Questoes genericas criadas pela versao antiga (sem IA); nao devem ser reaproveitadas
    private static boolean isLegacyGenericQuestion(Question q) {
        String text = q.getText() != null ? q.getText() : "";
        return text.startsWith("Qual dos conceitos a seguir melhor define o foco principal do estudo de ")
                || text.startsWith("Em relação às boas práticas aplicadas a ");
    }

    @Transactional(readOnly = true)
    public QuizDetailResponse getQuizForExecution(User user, Long quizId) {
        Quiz quiz = quizRepository.findByIdAndUserId(quizId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Simulado não encontrado com ID: " + quizId));

        return mapToDetailResponse(quiz);
    }

    @Transactional
    public QuizResultResponse submitQuiz(User user, Long quizId, QuizSubmitRequest request) {
        Quiz quiz = quizRepository.findByIdAndUserId(quizId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Simulado não encontrado com ID: " + quizId));

        if (quiz.getStatus() == QuizStatus.COMPLETED) {
            throw new IllegalStateException("Este simulado já foi finalizado e corrigido.");
        }

        List<QuizQuestion> quizQuestions = quizQuestionRepository.findByQuizIdOrderByOrderIndexAsc(quiz.getId());
        Map<Long, Question> questionMap = quizQuestions.stream()
                .collect(Collectors.toMap(qq -> qq.getQuestion().getId(), QuizQuestion::getQuestion));

        Map<Long, Long> submissionMap = new HashMap<>();
        if (request.getAnswers() != null) {
            for (AnswerItemRequest item : request.getAnswers()) {
                submissionMap.put(item.getQuestionId(), item.getSelectedOptionId());
            }
        }

        int correctCount = 0;
        List<QuizCorrectionItemResponse> results = new ArrayList<>();

        for (QuizQuestion qq : quizQuestions) {
            Question question = qq.getQuestion();
            Long selectedOptionId = submissionMap.get(question.getId());

            QuestionOption selectedOption = null;
            if (selectedOptionId != null) {
                selectedOption = questionOptionRepository.findByIdAndQuestionId(selectedOptionId, question.getId())
                        .orElse(null);
            }

            QuestionOption correctOption = question.getOptions().stream()
                    .filter(QuestionOption::isCorrect)
                    .findFirst()
                    .orElse(null);

            boolean isCorrect = selectedOption != null && selectedOption.isCorrect();
            if (isCorrect) {
                correctCount++;
            }

            QuizAnswer quizAnswer = new QuizAnswer(quiz, question, selectedOption, isCorrect);
            quiz.addAnswer(quizAnswer);
            quizAnswerRepository.save(quizAnswer);

            results.add(new QuizCorrectionItemResponse(
                    question.getId(),
                    question.getText(),
                    selectedOption != null ? selectedOption.getId() : null,
                    selectedOption != null ? selectedOption.getText() : "Não respondida",
                    correctOption != null ? correctOption.getId() : null,
                    correctOption != null ? correctOption.getText() : "Gabarito indisponível",
                    isCorrect,
                    question.getExplanation()
            ));
        }

        int total = quiz.getTotalQuestions() > 0 ? quiz.getTotalQuestions() : quizQuestions.size();
        double rawScore = ((double) correctCount / total) * 10.0;
        double roundedScore = Math.round(rawScore * 10.0) / 10.0;
        double percentage = Math.round(((double) correctCount / total) * 1000.0) / 10.0;

        quiz.setCorrectAnswers(correctCount);
        quiz.setScore(roundedScore);
        quiz.setStatus(QuizStatus.COMPLETED);
        quiz.setCompletedAt(LocalDateTime.now());
        quizRepository.save(quiz);

        // Simulado finalizado conta como dia de estudo (sequencia)
        studyActivityService.registerActivity(user, ActivityType.QUIZ);

        Map<String, Boolean> topicMap = new HashMap<>();
        for (QuizQuestion qq : quizQuestions) {
            String topicName = qq.getQuestion().getTopic() != null ? qq.getQuestion().getTopic().getName() : null;
            if (topicName != null) {
                Long qId = qq.getQuestion().getId();
                Long selId = submissionMap.get(qId);
                boolean ok = selId != null && qq.getQuestion().getOptions().stream().anyMatch(o -> o.getId().equals(selId) && o.isCorrect());
                topicMap.put(topicName, topicMap.getOrDefault(topicName, true) && ok);
            }
        }
        List<String> fortes = topicMap.entrySet().stream().filter(Map.Entry::getValue).map(Map.Entry::getKey).toList();
        List<String> fracos = topicMap.entrySet().stream().filter(e -> !e.getValue()).map(Map.Entry::getKey).toList();

        return new QuizResultResponse(
                quiz.getId(),
                quiz.getTitle(),
                quiz.getStatus().name(),
                total,
                correctCount,
                roundedScore,
                percentage,
                quiz.getCompletedAt(),
                results,
                fortes,
                fracos
        );
    }

    @Transactional(readOnly = true)
    public QuizResultResponse getQuizResult(User user, Long quizId) {
        Quiz quiz = quizRepository.findByIdAndUserId(quizId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Simulado não encontrado com ID: " + quizId));

        if (quiz.getStatus() != QuizStatus.COMPLETED) {
            throw new IllegalStateException("O simulado ainda não foi concluído. Submeta as respostas para ver o resultado.");
        }

        List<QuizQuestion> quizQuestions = quizQuestionRepository.findByQuizIdOrderByOrderIndexAsc(quiz.getId());
        List<QuizAnswer> answers = quizAnswerRepository.findByQuizId(quiz.getId());
        Map<Long, QuizAnswer> answerMap = answers.stream()
                .collect(Collectors.toMap(a -> a.getQuestion().getId(), a -> a, (a1, a2) -> a1));

        List<QuizCorrectionItemResponse> results = new ArrayList<>();
        Map<String, Boolean> topicMap = new HashMap<>();

        for (QuizQuestion qq : quizQuestions) {
            Question question = qq.getQuestion();
            QuizAnswer answer = answerMap.get(question.getId());

            QuestionOption correctOption = question.getOptions().stream()
                    .filter(QuestionOption::isCorrect)
                    .findFirst()
                    .orElse(null);

            QuestionOption selectedOption = answer != null ? answer.getSelectedOption() : null;
            boolean isCorrect = answer != null && answer.isCorrect();

            String topicName = question.getTopic() != null ? question.getTopic().getName() : null;
            if (topicName != null) {
                topicMap.put(topicName, topicMap.getOrDefault(topicName, true) && isCorrect);
            }

            results.add(new QuizCorrectionItemResponse(
                    question.getId(),
                    question.getText(),
                    selectedOption != null ? selectedOption.getId() : null,
                    selectedOption != null ? selectedOption.getText() : "Não respondida",
                    correctOption != null ? correctOption.getId() : null,
                    correctOption != null ? correctOption.getText() : "Gabarito indisponível",
                    isCorrect,
                    question.getExplanation()
            ));
        }

        List<String> fortes = topicMap.entrySet().stream().filter(Map.Entry::getValue).map(Map.Entry::getKey).toList();
        List<String> fracos = topicMap.entrySet().stream().filter(e -> !e.getValue()).map(Map.Entry::getKey).toList();

        int total = quiz.getTotalQuestions();
        int correctCount = quiz.getCorrectAnswers() != null ? quiz.getCorrectAnswers() : 0;
        double score = quiz.getScore() != null ? quiz.getScore() : 0.0;
        double percentage = total > 0 ? Math.round(((double) correctCount / total) * 1000.0) / 10.0 : 0.0;

        return new QuizResultResponse(
                quiz.getId(),
                quiz.getTitle(),
                quiz.getStatus().name(),
                total,
                correctCount,
                score,
                percentage,
                quiz.getCompletedAt(),
                results,
                fortes,
                fracos
        );
    }

    @Transactional(readOnly = true)
    public List<QuizResponse> listUserQuizzes(User user) {
        List<Quiz> quizzes = quizRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        return quizzes.stream()
                .map(this::mapToSummaryResponse)
                .collect(Collectors.toList());
    }

    private QuizDetailResponse mapToDetailResponse(Quiz quiz) {
        List<QuizQuestion> quizQuestions = quizQuestionRepository.findByQuizIdOrderByOrderIndexAsc(quiz.getId());
        List<QuestionResponse> questionResponses = quizQuestions.stream()
                .map(qq -> questionService.mapToPublicResponse(qq.getQuestion()))
                .collect(Collectors.toList());

        return new QuizDetailResponse(
                quiz.getId(),
                quiz.getTitle(),
                quiz.getTopic() != null ? quiz.getTopic().getId() : null,
                quiz.getTopic() != null ? quiz.getTopic().getName() : null,
                quiz.getSubject() != null ? quiz.getSubject().getId() : null,
                quiz.getSubject() != null ? quiz.getSubject().getName() : null,
                quiz.getTotalQuestions(),
                quiz.getStatus().name(),
                quiz.getCreatedAt(),
                questionResponses
        );
    }

    private QuizResponse mapToSummaryResponse(Quiz quiz) {
        return new QuizResponse(
                quiz.getId(),
                quiz.getTitle(),
                quiz.getTopic() != null ? quiz.getTopic().getId() : null,
                quiz.getTopic() != null ? quiz.getTopic().getName() : null,
                quiz.getSubject() != null ? quiz.getSubject().getId() : null,
                quiz.getSubject() != null ? quiz.getSubject().getName() : null,
                quiz.getTotalQuestions(),
                quiz.getCorrectAnswers(),
                quiz.getScore(),
                quiz.getStatus().name(),
                quiz.getCreatedAt(),
                quiz.getCompletedAt()
        );
    }

    /**
     * Contingencia quando a IA nao responde: cria a quantidade pedida de questoes de revisao,
     * todas citando o assunto e a materia, distribuidas entre os assuntos.
     */
    private List<Question> createFallbackQuestions(Subject subject, List<Topic> topics, int count) {
        List<Question> created = new ArrayList<>();
        String subjectName = subject.getName();
        for (int i = 0; i < count; i++) {
            Topic topic = topics.get(i % topics.size());
            String t = topic.getName();
            int template = (i / topics.size()) % 8;
            int round = (i / topics.size()) / 8;
            String suffix = round > 0 ? " (revisão " + (round + 1) + ")" : "";
            String[] parts = fallbackTemplate(template, t, subjectName, topic.getDescription());
            Question q = new Question(parts[0] + suffix,
                    parts[1] + " (Questão de revisão gerada sem IA: confira o conteúdo de \"" + t + "\" nos seus materiais.)",
                    topic);
            q.setSource("FALLBACK");
            List<QuestionOption> options = new ArrayList<>();
            options.add(new QuestionOption(limit(parts[2]), true));
            options.add(new QuestionOption(limit(parts[3]), false));
            options.add(new QuestionOption(limit(parts[4]), false));
            options.add(new QuestionOption(limit(parts[5]), false));
            Collections.shuffle(options);
            options.forEach(q::addOption);
            created.add(questionRepository.save(q));
        }
        return created;
    }

    // {enunciado, explicacao, correta, errada1, errada2, errada3}
    private static String[] fallbackTemplate(int n, String t, String s, String description) {
        String desc = description != null && !description.isBlank() ? description.trim() : null;
        return switch (n) {
            case 0 -> desc != null
                    ? new String[]{"De acordo com a descrição cadastrada, o assunto \"" + t + "\" de " + s + " trata de:",
                    "A descrição do assunto \"" + t + "\" é: " + desc + ".",
                    desc,
                    "Um tema sem relação com " + s + ".",
                    "Somente a história de quem criou " + s + ".",
                    "Regras de formatação de trabalhos, sem ligação com " + t + "."}
                    : new String[]{"O assunto \"" + t + "\" faz parte de qual matéria no seu plano de estudos?",
                    "\"" + t + "\" está cadastrado dentro da matéria " + s + ".",
                    s,
                    "Nenhuma matéria: \"" + t + "\" é um assunto avulso.",
                    "Todas as matérias ao mesmo tempo.",
                    "Uma matéria que ainda não foi criada."};
            case 1 -> new String[]{"Ao revisar \"" + t + "\" (" + s + "), qual atitude mostra que você realmente entendeu o assunto?",
                    "Explicar \"" + t + "\" com suas palavras e aplicar em exemplos mostra domínio do conteúdo.",
                    "Explicar \"" + t + "\" com suas palavras e resolver um exemplo sem consultar o material.",
                    "Reconhecer o nome \"" + t + "\" quando ele aparece no texto.",
                    "Ter lido o material de \"" + t + "\" uma vez, sem fazer exercícios.",
                    "Copiar o resumo de \"" + t + "\" de um colega."};
            case 2 -> new String[]{"Você errou uma questão de \"" + t + "\". Qual é o melhor próximo passo?",
                    "Entender por que errou em \"" + t + "\" evita repetir o mesmo erro.",
                    "Ler a explicação, revisar o ponto de \"" + t + "\" que causou o erro e refazer a questão.",
                    "Ignorar o erro e seguir para outro assunto de " + s + ".",
                    "Decorar a letra certa daquela questão de \"" + t + "\".",
                    "Desistir de \"" + t + "\" porque é difícil demais."};
            case 3 -> new String[]{"Qual material é mais útil para fixar os conceitos de \"" + t + "\"?",
                    "Um resumo próprio, com os conceitos-chave de \"" + t + "\", facilita a revisão antes da prova.",
                    "Um resumo próprio com os conceitos-chave de \"" + t + "\" e exemplos resolvidos.",
                    "Uma lista de respostas prontas sem os conceitos de \"" + t + "\".",
                    "Anotações de outra matéria que não " + s + ".",
                    "Nenhum material: só ouvir a aula de \"" + t + "\" uma vez basta."};
            case 4 -> new String[]{"Para se preparar para uma prova de " + s + " que cobra \"" + t + "\", o mais eficiente é:",
                    "Treinar com questões no formato da prova revela as lacunas em \"" + t + "\".",
                    "Resolver simulados com questões de \"" + t + "\" e revisar os erros.",
                    "Estudar \"" + t + "\" só na véspera, de madrugada.",
                    "Ler apenas os títulos dos capítulos de " + s + ".",
                    "Pular \"" + t + "\" e torcer para não cair."};
            case 5 -> new String[]{"Como relacionar \"" + t + "\" com outros assuntos de " + s + "?",
                    "Ligar \"" + t + "\" aos outros assuntos de " + s + " ajuda a lembrar e aplicar o conteúdo.",
                    "Montar um mapa mental ligando \"" + t + "\" aos assuntos vizinhos de " + s + ".",
                    "Estudar \"" + t + "\" isolado, sem pensar no restante de " + s + ".",
                    "Evitar comparar \"" + t + "\" com qualquer outro conteúdo.",
                    "Trocar " + s + " por outra matéria sempre que \"" + t + "\" aparecer."};
            case 6 -> new String[]{"Qual a melhor forma de usar o tutor de IA do Trilha+ para estudar \"" + t + "\"?",
                    "Perguntas específicas sobre \"" + t + "\" geram explicações mais úteis.",
                    "Fazer perguntas específicas sobre os pontos de \"" + t + "\" que ainda não ficaram claros.",
                    "Pedir apenas a resposta final, sem entender \"" + t + "\".",
                    "Perguntar sobre assuntos sem relação com " + s + ".",
                    "Não usar o tutor e não revisar \"" + t + "\"."};
            default -> new String[]{"Com que frequência vale revisar \"" + t + "\" para não esquecer?",
                    "Revisões espaçadas mantêm \"" + t + "\" na memória por mais tempo.",
                    "Em revisões espaçadas (ex.: 1 dia, 1 semana e 1 mês depois) de \"" + t + "\".",
                    "Uma única vez; depois \"" + t + "\" nunca mais é esquecido.",
                    "Só quando aparecer uma prova de " + s + ".",
                    "Nunca: revisar \"" + t + "\" atrapalha o aprendizado."};
        };
    }

    private static String limit(String text) {
        return text.length() > 255 ? text.substring(0, 252) + "..." : text;
    }
}
