package com.pub_game_be.service;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class QuestionGeneratorService {

    private final String GROQ_API_URL = "https://api.groq.com/openai/v1/chat/completions";

    @Value("${groq.api.key}")
    private String apiKey;

    public String generateQuestionJson(String category, String type, String difficulty) {
        RestTemplate restTemplate = new RestTemplate();

        String difficultyContext = switch (difficulty.toLowerCase()) {
            case "facile" -> "Usa domande molto popolari, quasi ovvie. Adatte a chi non ha studiato l'argomento.";
            case "medio" -> "Usa domande che richiedono una buona conoscenza generale. Evita dettagli troppo oscuri.";
            case "difficile" ->
                    "Sii molto specifico. Usa dettagli che solo un esperto della materia conoscerebbe. Sfida i giocatori.";
            default -> "Difficoltà bilanciata.";
        };

        String chronoConstraint = "";
        if ("CHRONO".equalsIgnoreCase(type)) {
            chronoConstraint = "IMPORTANTE: Per la modalità CHRONO, l'evento deve essere accaduto tra l'anno 1000 e l'anno attuale. " +
                    "Assicurati che la 'correctAnswer' sia esclusivamente un numero intero (l'anno).";
        }

        String prompt = String.format(
                "Sei il presentatore di un quiz televisivo. Genera una domanda per la categoria %s di tipo %s.\n" +
                        "LIVELLO DI DIFFICOLTÀ: %s (%s).\n" +
                        "%s\n" + // Inseriamo il vincolo qui
                        "Rispondi SOLO con un oggetto JSON valido (no testo extra) con questi campi:\n" +
                        "- question: il testo della domanda\n" +
                        "- options: array di 4 stringhe (QUIZ), array di 2 stringhe ['VERO','FALSO'] (TRUE_FALSE), null per CHRONO\n" +
                        "- correctAnswer: stringa (la risposta esatta o l'anno esatto)\n" +
                        "- type: la stringa %s",
                category, type, difficulty, difficultyContext, chronoConstraint, type
        );

        String imageContext = "";
        if ("IMAGE_BLUR".equalsIgnoreCase(type)) {
            prompt = String.format(
                    "Sei un autore di quiz. Genera un round 'IMAGE_BLUR' su una celebrità mondiale (attori, cantanti, scienziati).\n" +
                            "REGOLE:\n" +
                            "1. Scegli un personaggio famoso.\n" +
                            "2. 'question' deve essere sempre: 'Riconosci la celebrità?'\n" +
                            "3. 'options' deve essere NULL.\n" +
                            "4. 'correctAnswer' deve essere il nome del personaggio.\n" +
                            "5. AGGIUNGI UN CAMPO 'imageUrl' usando esattamente questo formato: https://en.wikipedia.org/wiki/Special:FilePath/NOME_CELEBRITA\n" +
                            "(Esempio: https://en.wikipedia.org/wiki/Special:FilePath/Elon_Musk)\n\n" +
                            "Rispondi SOLO in JSON valido."
            );
        }

        Map<String, Object> request = new HashMap<>();
        request.put("model", "llama-3.3-70b-versatile");
        request.put("messages", List.of(Map.of("role", "user", "content", prompt)));

        HttpHeaders headers = new HttpHeaders();
        // Usiamo la variabile iniettata apiKey
        headers.setBearerAuth(apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(GROQ_API_URL, entity, String.class);
            String rawContent = parseJsonResponse(response.getBody());
            return cleanAiJson(rawContent);
        } catch (Exception e) {
            System.err.println("Errore durante la chiamata a Groq: " + e.getMessage());
            return "{}";
        }
    }

    private String cleanAiJson(String content) {
        if (content == null) return "{}";

        if (content.contains("```json")) {
            content = content.substring(content.indexOf("```json") + 7);
            content = content.substring(0, content.lastIndexOf("```"));
        } else if (content.contains("```")) {
            content = content.substring(content.indexOf("```") + 3);
            content = content.substring(0, content.lastIndexOf("```"));
        }

        int firstBrace = content.indexOf("{");
        int lastBrace = content.lastIndexOf("}");

        if (firstBrace >= 0 && lastBrace >= 0) {
            return content.substring(firstBrace, lastBrace + 1).trim();
        }

        return content.trim();
    }

    private String parseJsonResponse(String responseBody) {
        JSONObject json = new JSONObject(responseBody);
        return json.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content");
    }
}