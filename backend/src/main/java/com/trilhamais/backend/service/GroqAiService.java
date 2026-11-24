package com.trilhamais.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trilhamais.backend.dto.AiAskRequest;
import com.trilhamais.backend.dto.AiExplanationResponse;
import com.trilhamais.backend.dto.AiSummarizeRequest;
import com.trilhamais.backend.dto.AiSummarizeResponse;
import com.trilhamais.backend.model.StudyMaterial;
import com.trilhamais.backend.model.Topic;
import com.trilhamais.backend.model.User;
import com.trilhamais.backend.repository.StudyMaterialRepository;
import com.trilhamais.backend.repository.TopicRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 * Servico de integracao com a API gratuita da Groq Cloud (Llama 3.3).
 * Oferece suporte a esclarecimento didatico de duvidas e geracao automatica de resumos.
 * Possui motor de contingencia pedagogico quando a chave nao esta configurada.
 */
@Service
public class GroqAiService {

    private static final Logger log = LoggerFactory.getLogger(GroqAiService.class);

    private final String apiKey;
    private final String apiUrl;
    private final String model;
    private final TopicRepository topicRepository;
    private final StudyMaterialRepository studyMaterialRepository;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public GroqAiService(
            @Value("${groq.api.key:}") String apiKey,
            @Value("${groq.api.url:https://api.groq.com/openai/v1/chat/completions}") String apiUrl,
            @Value("${groq.model:openai/gpt-oss-120b}") String model,
            TopicRepository topicRepository,
            StudyMaterialRepository studyMaterialRepository) {
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.apiUrl = (apiUrl != null && !apiUrl.isBlank()) ? apiUrl.trim() : "https://api.groq.com/openai/v1/chat/completions";
        this.model = (model != null && !model.isBlank()) ? model.trim() : "openai/gpt-oss-120b";
        this.topicRepository = topicRepository;
        this.studyMaterialRepository = studyMaterialRepository;
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Responde a duvida de um estudante utilizando contexto de estudo e o modelo Llama via Groq.
     */
    public AiExplanationResponse askQuestion(AiAskRequest request, User user) {
        StringBuilder contextBuilder = new StringBuilder();
        String topicName = "Geral";

        if (request.getTopicId() != null) {
            Optional<Topic> topicOpt = topicRepository.findByIdAndSubjectUserId(request.getTopicId(), user.getId());
            if (topicOpt.isPresent()) {
                Topic topic = topicOpt.get();
                topicName = topic.getName();
                contextBuilder.append("Assunto de Estudo: ").append(topic.getName()).append("\n");
                if (topic.getDescription() != null) {
                    contextBuilder.append("Ementa do Assunto: ").append(topic.getDescription()).append("\n");
                }
            }
        }

        if (request.getMaterialId() != null) {
            Optional<StudyMaterial> matOpt = studyMaterialRepository.findByIdAndTopicSubjectUserId(request.getMaterialId(), user.getId());
            if (matOpt.isPresent()) {
                StudyMaterial mat = matOpt.get();
                contextBuilder.append("Material Didático: ").append(mat.getTitle()).append("\n");
                contextBuilder.append("Conteúdo do Material: ").append(mat.getContent()).append("\n");
            }
        }

        if (request.getCustomContext() != null && !request.getCustomContext().isBlank()) {
            contextBuilder.append("Contexto Adicional: ").append(request.getCustomContext().trim()).append("\n");
        }

        String promptContext = contextBuilder.toString();
        String answer;

        if (isGroqConfigured()) {
            try {
                answer = callGroqChat(
                        "Você é o tutor inteligente da plataforma acadêmica Trilha+. Responda à dúvida do estudante de forma clara, didática e motivadora. Se houver contexto de estudo, priorize-o.",
                        "Contexto:\n" + promptContext + "\nPergunta do estudante: " + request.getQuestion()
                );
            } catch (Exception e) {
                log.warn("Falha na chamada à API da Groq: {}. Utilizando fallback pedagógico.", e.getMessage());
                answer = generateFallbackExplanation(request.getQuestion(), topicName, promptContext);
            }
        } else {
            answer = generateFallbackExplanation(request.getQuestion(), topicName, promptContext);
        }

        List<String> suggestions = List.of(
                "Poderia me dar um exemplo prático aplicado a esse assunto?",
                "Quais os erros mais comuns cometidos nesse tópico?",
                "Como esse conteúdo costuma ser cobrado em simulados e provas?"
        );

        return new AiExplanationResponse(answer, topicName, "Groq Cloud (" + model + ")", suggestions);
    }

    /**
     * Gera um resumo inteligente de um conteudo de estudo via IA.
     */
    public AiSummarizeResponse summarize(AiSummarizeRequest request, User user) {
        String topicName = request.getTopicName() != null ? request.getTopicName() : "Conteúdo";
        String summary;
        List<String> keyPoints = new ArrayList<>();

        if (isGroqConfigured()) {
            try {
                String response = callGroqChat(
                        "Você é o tutor da plataforma Trilha+. Resuma o texto fornecido pelo estudante de maneira concisa em um parágrafo. Em seguida, liste de 3 a 5 pontos-chave essenciais para revisão.",
                        "Texto a resumir:\n" + request.getContent()
                );
                summary = response;
                keyPoints = List.of(
                        "Conceitos principais consolidados para revisão",
                        "Fundamentos essenciais sintetizados para resolução de questões",
                        "Estrutura lógica pronta para fixação de memória"
                );
            } catch (Exception e) {
                log.warn("Falha na API da Groq no resumo: {}. Utilizando fallback.", e.getMessage());
                summary = generateFallbackSummary(request.getContent(), topicName);
                keyPoints = generateFallbackKeyPoints(topicName);
            }
        } else {
            summary = generateFallbackSummary(request.getContent(), topicName);
            keyPoints = generateFallbackKeyPoints(topicName);
        }

        return new AiSummarizeResponse(summary, keyPoints, "Groq Cloud (" + model + ")");
    }

    private boolean isGroqConfigured() {
        return apiKey != null && !apiKey.isBlank() && !apiKey.equalsIgnoreCase("none");
    }

    private String callGroqChat(String systemPrompt, String userMessage) throws Exception {
        Map<String, Object> bodyMap = new HashMap<>();
        bodyMap.put("model", model);
        bodyMap.put("temperature", 0.6);
        bodyMap.put("max_tokens", 2000);

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        messages.add(Map.of("role", "user", "content", userMessage));
        bodyMap.put("messages", messages);

        String jsonPayload = objectMapper.writeValueAsString(bodyMap);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
            if (!contentNode.isMissingNode()) {
                return contentNode.asText().trim();
            }
        }

        throw new RuntimeException("Resposta da Groq com status " + response.statusCode() + ": " + response.body());
    }

    private String generateFallbackExplanation(String question, String topicName, String context) {
        return "Olá! Como tutor inteligente do Trilha+, analisei sua dúvida sobre " + topicName + ":\n\n" +
                "Com base nos fundamentos deste assunto, para compreender \"" + question + "\", " +
                "é fundamental associar a teoria à prática de resolução de questões. " +
                (context.isBlank() ? "" : "Identifiquei que os materiais vinculados destacam pontos centrais para fixação deste tópico. ") +
                "Recomendo revisar os conceitos básicos da disciplina e testar seus conhecimentos no próximo simulado do Trilha+!";
    }

    private String generateFallbackSummary(String content, String topicName) {
        String trimmed = content.trim();
        String preview = trimmed.length() > 180 ? trimmed.substring(0, 180) + "..." : trimmed;
        return "Resumo Estruturado (" + topicName + "): " + preview +
                " — Este conteúdo sintetiza os conceitos centrais do módulo, destacando as definições indispensáveis para o domínio do tema e preparação para avaliações.";
    }

    private List<String> generateFallbackKeyPoints(String topicName) {
        return List.of(
                "Compreensão das definições conceituais de " + topicName,
                "Identificação dos padrões e regras de aplicação prática",
                "Fixação com foco em resolução rápida de exercícios"
        );
    }

    public String generateStudyPlan(List<String> weakTopicNames) {
        if (weakTopicNames == null || weakTopicNames.isEmpty()) {
            return "Parabéns pelo seu desempenho! Todos os tópicos avaliados apresentaram taxa de acerto satisfatória. Continue praticando simulados periodicamente para consolidar o aprendizado.";
        }

        String topicsFormatted = String.join(", ", weakTopicNames);

        if (isGroqConfigured()) {
            try {
                String systemPrompt = "Você é o tutor acadêmico de inteligência artificial da plataforma Trilha+. " +
                        "Seu objetivo é gerar um plano de estudos objetivo e motivador em língua portuguesa " +
                        "com recomendações práticas e passos de estudo para que o aluno supere suas dificuldades específicas.";
                String userMessage = "O estudante realizou simulados avaliativos e apresentou taxa de acerto baixa nos seguintes assuntos: " +
                        topicsFormatted + ". Elabore um plano de ação em 3 passos práticos para o estudante recuperar o domínio desses conteúdos.";

                return callGroqChat(systemPrompt, userMessage);
            } catch (Exception e) {
                log.warn("Falha ao chamar Groq Cloud para plano de estudo, ativando plano didático de contingência: {}", e.getMessage());
            }
        }

        return "Plano de Estudo Personalizado Trilha+:\n\n" +
                "1. Revisão Teórica Imediata: Dedique 30 minutos para reler os materiais e resumos dos assuntos: " + topicsFormatted + ".\n" +
                "2. Mapeamento de Falhas: Analise as questões que errou no último simulado e identifique se a dúvida foi conceitual ou de interpretação.\n" +
                "3. Novo Simulado de Fixação: Gere um simulado focado de 10 questões desses tópicos no Trilha+ para validar sua evolução.";
    }

    /**
     * Questao de multipla escolha gerada pela IA (4 alternativas, indice da correta de 0 a 3).
     */
    public record GeneratedQuestion(String text, List<String> options, int correctIndex, String explanation, String topic) {
    }

    /**
     * Gera questoes de multipla escolha com a IA (Groq). Pede JSON, extrai o JSON mesmo se vier dentro de ```json,
     * valida cada questao e, se faltar questao ou a chamada falhar, tenta mais 1 vez.
     * Devolve a lista (pode vir menor que o pedido ou vazia se a IA nao estiver disponivel).
     */
    public List<GeneratedQuestion> generateQuizQuestions(String subjectName, String topicName, String topicDescription,
                                                         List<String> topicNames, int count) {
        List<GeneratedQuestion> result = new ArrayList<>();
        if (count <= 0 || !isGroqConfigured()) {
            return result;
        }
        Set<String> seen = new HashSet<>();
        for (int attempt = 1; attempt <= 2 && result.size() < count; attempt++) {
            int needed = count - result.size();
            try {
                String content = callGroqForQuiz(
                        buildQuizUserPrompt(subjectName, topicName, topicDescription, topicNames, needed, result),
                        needed, attempt == 1);
                for (GeneratedQuestion q : parseGeneratedQuestions(content)) {
                    if (result.size() >= count) {
                        break;
                    }
                    if (seen.add(q.text().toLowerCase(Locale.ROOT).replaceAll("\\s+", " "))) {
                        result.add(q);
                    }
                }
            } catch (Exception e) {
                log.warn("Falha ao gerar questões com a Groq (tentativa {}): {}", attempt, e.getMessage());
            }
        }
        if (result.size() < count) {
            log.warn("IA gerou {} de {} questões pedidas; o restante usa o banco de contingência.", result.size(), count);
        }
        return result;
    }

    private String buildQuizUserPrompt(String subjectName, String topicName, String topicDescription,
                                       List<String> topicNames, int count, List<GeneratedQuestion> already) {
        StringBuilder sb = new StringBuilder();
        sb.append("Crie exatamente ").append(count).append(" questões de múltipla escolha ");
        if (topicName != null && !topicName.isBlank()) {
            sb.append("sobre o assunto \"").append(topicName).append("\" da matéria \"").append(subjectName).append("\".\n");
        } else {
            sb.append("sobre a matéria \"").append(subjectName).append("\".\n");
        }
        if (topicDescription != null && !topicDescription.isBlank()) {
            sb.append("Descrição do assunto: ").append(topicDescription.trim()).append("\n");
        }
        if (topicNames != null && topicNames.size() > 1) {
            sb.append("Assuntos desta matéria (distribua as questões entre eles): ")
                    .append(String.join("; ", topicNames)).append("\n");
        }
        if (!already.isEmpty()) {
            sb.append("Não repita estas questões já criadas: ");
            for (GeneratedQuestion q : already) {
                sb.append("\"").append(q.text()).append("\" ");
            }
            sb.append("\n");
        }
        sb.append("""
                Regras:
                - Cada questão tem exatamente 4 alternativas e somente 1 correta.
                - Questões sobre o CONTEÚDO do assunto (conceitos, exemplos, aplicação), nada genérico sobre "como estudar".
                - Alternativas erradas plausíveis; não use "todas as anteriores" nem "nenhuma das anteriores".
                - Explicação curta (1 ou 2 frases) dizendo por que a alternativa correta está certa.
                - Tudo em português do Brasil.
                Responda SOMENTE com um JSON válido, sem texto antes ou depois, neste formato:
                {"questoes":[{"enunciado":"...","alternativas":["...","...","...","..."],"correta":0,"explicacao":"...","assunto":"..."}]}
                Em "correta" use o índice da alternativa correta (0 a 3). Em "assunto" coloque o nome do assunto da questão.
                """);
        return sb.toString();
    }

    private String callGroqForQuiz(String userMessage, int count, boolean jsonMode) throws Exception {
        Map<String, Object> bodyMap = new HashMap<>();
        bodyMap.put("model", model);
        bodyMap.put("temperature", 0.7);
        bodyMap.put("max_tokens", Math.min(32000, 3000 + count * 450));
        if (model.contains("gpt-oss")) {
            // Modelo com raciocinio: esforco baixo para sobrar tokens para a resposta
            bodyMap.put("reasoning_effort", "low");
        }
        if (jsonMode) {
            bodyMap.put("response_format", Map.of("type", "json_object"));
        }
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content",
                "Você é um professor que elabora questões de múltipla escolha para estudantes brasileiros. "
                        + "Responda sempre e somente com JSON válido."));
        messages.add(Map.of("role", "user", "content", userMessage));
        bodyMap.put("messages", messages);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(bodyMap)))
                .build();

        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            JsonNode contentNode = objectMapper.readTree(response.body()).path("choices").path(0).path("message").path("content");
            if (!contentNode.isMissingNode() && !contentNode.isNull() && !contentNode.asText().isBlank()) {
                return contentNode.asText();
            }
            throw new RuntimeException("Resposta da Groq sem conteúdo.");
        }
        String body = response.body();
        throw new RuntimeException("Groq status " + response.statusCode() + ": "
                + (body != null && body.length() > 300 ? body.substring(0, 300) : body));
    }

    /**
     * Extrai e valida as questoes do texto da IA (aceita JSON puro, dentro de ```json ou com texto em volta).
     */
    List<GeneratedQuestion> parseGeneratedQuestions(String content) throws Exception {
        String json = extractJson(content);
        JsonNode root = objectMapper.readTree(json);
        JsonNode array = root;
        if (root.isObject()) {
            array = firstPresent(root, "questoes", "questões", "questions", "itens", "items");
            if (array == null) {
                // Pode vir uma unica questao como objeto
                array = objectMapper.createArrayNode().add(root);
            }
        }
        List<GeneratedQuestion> list = new ArrayList<>();
        if (array == null || !array.isArray()) {
            return list;
        }
        for (JsonNode node : array) {
            GeneratedQuestion q = toQuestion(node);
            if (q != null) {
                list.add(q);
            }
        }
        return list;
    }

    private GeneratedQuestion toQuestion(JsonNode node) {
        if (node == null || !node.isObject()) {
            return null;
        }
        JsonNode textNode = firstPresent(node, "enunciado", "pergunta", "question", "text");
        JsonNode optionsNode = firstPresent(node, "alternativas", "opcoes", "opções", "options");
        JsonNode correctNode = firstPresent(node, "correta", "resposta", "correct", "answer", "correctIndex");
        JsonNode explanationNode = firstPresent(node, "explicacao", "explicação", "explanation");
        JsonNode topicNode = firstPresent(node, "assunto", "topic");
        if (textNode == null || optionsNode == null || !optionsNode.isArray() || correctNode == null) {
            return null;
        }
        String text = textNode.asText("").trim();
        if (text.isEmpty()) {
            return null;
        }
        List<String> options = new ArrayList<>();
        for (JsonNode opt : optionsNode) {
            String o = opt.isObject() ? firstText(opt, "texto", "text") : opt.asText("");
            o = stripLetterPrefix(o.trim());
            if (o.length() > 255) {
                o = o.substring(0, 252) + "...";
            }
            options.add(o);
        }
        if (options.size() < 4) {
            return null;
        }
        int correct = resolveCorrectIndex(correctNode, options);
        if (correct < 0 || correct >= 4) {
            return null;
        }
        options = new ArrayList<>(options.subList(0, 4));
        Set<String> distinct = new HashSet<>();
        for (String o : options) {
            if (o.isBlank() || !distinct.add(o.toLowerCase(Locale.ROOT))) {
                return null;
            }
        }
        String explanation = explanationNode != null ? explanationNode.asText("").trim() : "";
        String topic = topicNode != null ? topicNode.asText("").trim() : null;
        return new GeneratedQuestion(text, options, correct, explanation.isEmpty() ? null : explanation, topic);
    }

    private int resolveCorrectIndex(JsonNode correctNode, List<String> options) {
        if (correctNode.isInt() || correctNode.isLong()) {
            return correctNode.asInt();
        }
        String value = correctNode.asText("").trim();
        if (value.matches("\\d+")) {
            return Integer.parseInt(value);
        }
        if (value.length() == 1 && Character.isLetter(value.charAt(0))) {
            return Character.toUpperCase(value.charAt(0)) - 'A';
        }
        String stripped = stripLetterPrefix(value);
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).equalsIgnoreCase(stripped)) {
                return i;
            }
        }
        return -1;
    }

    private static String stripLetterPrefix(String option) {
        // Remove "A) ", "b. ", "(C) " que a IA as vezes coloca antes da alternativa
        return option.replaceFirst("^\\(?[A-Da-d][).:-]\\s+", "").trim();
    }

    private static JsonNode firstPresent(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode child = node.get(name);
            if (child != null && !child.isNull()) {
                return child;
            }
        }
        return null;
    }

    private static String firstText(JsonNode node, String... names) {
        JsonNode child = firstPresent(node, names);
        return child != null ? child.asText("") : "";
    }

    static String extractJson(String content) {
        if (content == null) {
            throw new IllegalArgumentException("Resposta vazia da IA.");
        }
        String text = content.trim();
        int fence = text.indexOf("```");
        if (fence >= 0) {
            int start = text.indexOf('\n', fence);
            int end = text.indexOf("```", start > 0 ? start : fence + 3);
            if (start > 0 && end > start) {
                text = text.substring(start + 1, end).trim();
            }
        }
        int objStart = text.indexOf('{');
        int arrStart = text.indexOf('[');
        int start;
        char close;
        if (objStart < 0 && arrStart < 0) {
            throw new IllegalArgumentException("A resposta da IA não contém JSON.");
        } else if (arrStart >= 0 && (objStart < 0 || arrStart < objStart)) {
            start = arrStart;
            close = ']';
        } else {
            start = objStart;
            close = '}';
        }
        int end = text.lastIndexOf(close);
        if (end <= start) {
            throw new IllegalArgumentException("JSON incompleto na resposta da IA.");
        }
        return text.substring(start, end + 1);
    }

    public String getProviderName() {
        return isGroqConfigured() ? "Groq Cloud (" + model + ")" : "Contingência Pedagógica Trilha+";
    }

    public String chat(List<com.trilhamais.backend.dto.AiChatMessageDto> messageDtos) {
        String systemPrompt = "Você é o assistente de estudos do app Trilha+. Responda sempre em português do Brasil, " +
                "de forma clara, didática e objetiva, com exemplos curtos quando ajudar. " +
                "Se a pergunta não for sobre estudos, responda brevemente e traga a conversa de volta aos estudos.";

        if (isGroqConfigured()) {
            try {
                Map<String, Object> bodyMap = new HashMap<>();
                bodyMap.put("model", model);
                bodyMap.put("temperature", 0.6);
                bodyMap.put("max_tokens", 2000);

                List<Map<String, String>> messages = new ArrayList<>();
                messages.add(Map.of("role", "system", "content", systemPrompt));

                if (messageDtos != null) {
                    for (com.trilhamais.backend.dto.AiChatMessageDto msg : messageDtos) {
                        String role = msg.getRole() != null ? msg.getRole() : "user";
                        String content = msg.getContent() != null ? msg.getContent() : "";
                        messages.add(Map.of("role", role, "content", content));
                    }
                }
                bodyMap.put("messages", messages);

                String jsonPayload = objectMapper.writeValueAsString(bodyMap);

                HttpRequest httpRequest = HttpRequest.newBuilder()
                        .uri(URI.create(apiUrl))
                        .header("Authorization", "Bearer " + apiKey)
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(20))
                        .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                        .build();

                HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    JsonNode root = objectMapper.readTree(response.body());
                    JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
                    if (!contentNode.isMissingNode()) {
                        return contentNode.asText().trim();
                    }
                }
                log.warn("Erro status Groq: {} body: {}", response.statusCode(), response.body());
            } catch (Exception e) {
                log.warn("Falha no chat Groq: {}", e.getMessage());
            }
        }

        // Resposta de contingência pedagógica quando a chave da Groq não estiver presente
        String lastQuestion = "";
        if (messageDtos != null && !messageDtos.isEmpty()) {
            String content = messageDtos.get(messageDtos.size() - 1).getContent();
            lastQuestion = content != null ? content : "";
        }
        return "Olá! Sou o Assistente de Estudos Trilha+.\n\n" +
                "Para sua dúvida sobre \"" + (lastQuestion.isBlank() ? "seus estudos" : lastQuestion) + "\": " +
                "organize seus tópicos em teoria, mapas mentais e resolução de questões diárias. " +
                "Configure a chave da Groq em application.properties para respostas analíticas aprofundadas em tempo real!";
    }
}
