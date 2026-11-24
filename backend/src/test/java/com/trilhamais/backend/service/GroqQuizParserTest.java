package com.trilhamais.backend.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testa a leitura do JSON das questoes geradas pela IA (sem chamar a Groq).
 */
class GroqQuizParserTest {

    private final GroqAiService service = new GroqAiService("", null, null, null, null);

    @Test
    @DisplayName("Extrai o JSON mesmo dentro de ```json e com texto em volta")
    void shouldParseFencedJson() throws Exception {
        String content = """
                Claro! Aqui estão as questões:
                ```json
                {"questoes":[
                  {"enunciado":"Qual organela produz energia?","alternativas":["A) Mitocôndria","B) Ribossomo","C) Lisossomo","D) Núcleo"],"correta":0,"explicacao":"Respiração celular.","assunto":"Citologia"},
                  {"enunciado":"Qual a unidade da vida?","alternativas":["Átomo","Célula","Órgão","Tecido"],"correta":"B","explicacao":"A célula."}
                ]}
                ```
                Bons estudos!
                """;
        List<GroqAiService.GeneratedQuestion> list = service.parseGeneratedQuestions(content);
        assertEquals(2, list.size());
        assertEquals("Mitocôndria", list.get(0).options().get(0));
        assertEquals(0, list.get(0).correctIndex());
        assertEquals("Citologia", list.get(0).topic());
        assertEquals(1, list.get(1).correctIndex());
    }

    @Test
    @DisplayName("Descarta questões inválidas (menos de 4 alternativas, índice fora, alternativas repetidas)")
    void shouldDropInvalidQuestions() throws Exception {
        String content = """
                [{"enunciado":"Ok?","alternativas":["a","b","c","d"],"correta":3},
                 {"enunciado":"Poucas","alternativas":["a","b"],"correta":0},
                 {"enunciado":"Fora","alternativas":["a","b","c","d"],"correta":7},
                 {"enunciado":"Repetida","alternativas":["a","a","c","d"],"correta":0},
                 {"enunciado":"","alternativas":["a","b","c","d"],"correta":0}]
                """;
        List<GroqAiService.GeneratedQuestion> list = service.parseGeneratedQuestions(content);
        assertEquals(1, list.size());
        assertEquals("Ok?", list.get(0).text());
    }

    @Test
    @DisplayName("Sem chave da Groq não gera nada (o simulado cai na contingência)")
    void shouldReturnEmptyWithoutKey() {
        assertTrue(service.generateQuizQuestions("Biologia", "Citologia", null, List.of("Citologia"), 5).isEmpty());
    }
}
