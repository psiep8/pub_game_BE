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
            case "facile" -> "Usa personaggi/domande molto popolari, quasi ovvi. Adatte a chi non ha studiato l'argomento.";
            case "medio" -> "Usa personaggi/domande che richiedono una buona conoscenza generale. Evita dettagli troppo oscuri.";
            case "difficile" -> "Sii molto specifico. Usa dettagli che solo un esperto della materia conoscerebbe. Sfida i giocatori.";
            default -> "Difficoltà bilanciata.";
        };

        String prompt;

        // ========== IMAGE_BLUR: Logica speciale ==========
        if ("IMAGE_BLUR".equalsIgnoreCase(type)) {
            prompt = String.format(
                    "Sei il presentatore di un quiz televisivo basato sul riconoscimento visivo.\n" +
                            "CATEGORIA: %s\n" +
                            "LIVELLO DI DIFFICOLTÀ: %s (%s).\n\n" +
                            "Scegli una celebrità o personaggio famoso mondiale coerente con la difficoltà.\n" +
                            "Rispondi SOLO con un oggetto JSON valido (NO markdown, NO testo extra):\n" +
                            "{\n" +
                            "  \"question\": \"Riconosci la celebrità?\",\n" +
                            "  \"correctAnswer\": \"Nome Completo\",\n" +
                            "  \"imageUrl\": \"URL_DIRETTO_IMMAGINE\",\n" +
                            "  \"type\": \"IMAGE_BLUR\",\n" +
                            "  \"options\": null\n" +
                            "}\n\n" +
                            "REGOLE CRITICHE PER imageUrl:\n" +
                            "1. Usa il servizio Special:FilePath di Wikimedia Commons.\n" +
                            "2. Formato OBBLIGATORIO: https://commons.wikimedia.org/wiki/Special:FilePath/Nome_Cognome.jpg\n" +
                            "3. Sostituisci gli spazi con underscore (_).\n" +
                            "4. Assicurati che il nome sia quello della pagina Wikipedia inglese.\n" +
                            "5. ESEMPIO: Per Leonardo DiCaprio l'URL deve essere: https://commons.wikimedia.org/wiki/Special:FilePath/Leonardo_DiCaprio.jpg\n" +
                            "6. ESEMPIO: Per Chris Hemsworth l'URL deve essere: https://commons.wikimedia.org/wiki/Special:FilePath/Chris_Hemsworth.jpg\n" +
                            "7. Aggiungi SEMPRE '.jpg' alla fine del nome del file.",
                    category, difficulty, difficultyContext
            );
        }
        // ========== ALTRI TIPI: Logica normale ==========
        else {
            prompt = String.format(
                    "Sei il presentatore di un quiz televisivo. Genera una domanda per la categoria %s di tipo %s.\n" +
                            "LIVELLO DI DIFFICOLTÀ: %s (%s).\n" +
                            "Rispondi SOLO con un oggetto JSON valido (NO testo extra, NO markdown) con questi campi:\n" +
                            "- question: il testo della domanda\n" +
                            "- options: array di 4 stringhe (QUIZ), array di 2 stringhe ['VERO','FALSO'] (TRUE_FALSE), null per CHRONO\n" +
                            "- correctAnswer: stringa (la risposta esatta o l'anno esatto per CHRONO)\n" +
                            "- type: la stringa %s",
                    category, type, difficulty, difficultyContext, type
            );
        }

        Map<String, Object> request = new HashMap<>();
        request.put("model", "llama-3.3-70b-versatile");
        request.put("messages", List.of(Map.of("role", "user", "content", prompt)));
        request.put("temperature", 0.7); // Più creatività

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(GROQ_API_URL, entity, String.class);
            String rawContent = parseJsonResponse(response.getBody());
            String cleanedJson = cleanAiJson(rawContent);

            // Log per debug
            System.out.println("=== AI Response for " + type + " ===");
            System.out.println(cleanedJson);
            System.out.println("===================================");

            return cleanedJson;
        } catch (Exception e) {
            System.err.println("Errore durante la chiamata a Groq: " + e.getMessage());
            e.printStackTrace();
            return getFallbackJson(type);
        }
    }

    private String cleanAiJson(String content) {
        if (content == null) return "{}";

        // Rimuovi markdown code blocks
        if (content.contains("```json")) {
            content = content.substring(content.indexOf("```json") + 7);
            content = content.substring(0, content.lastIndexOf("```"));
        } else if (content.contains("```")) {
            content = content.substring(content.indexOf("```") + 3);
            content = content.substring(0, content.lastIndexOf("```"));
        }

        // Estrai solo il JSON valido
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

    /**
     * Fallback JSON in caso di errore API
     */
    private String getFallbackJson(String type) {
        if ("IMAGE_BLUR".equalsIgnoreCase(type)) {
            return """
                {
                    "question": "Chi è questa persona?",
                    "correctAnswer": "Tom Hanks",
                    "imageUrl": "https://en.wikipedia.org/wiki/Special:FilePath/Tom_Hanks",
                    "type": "IMAGE_BLUR",
                    "options": null
                }
                """;
        } else if ("QUIZ".equalsIgnoreCase(type)) {
            return """
                {
                    "question": "Qual è la capitale dell'Italia?",
                    "options": ["Roma", "Milano", "Napoli", "Firenze"],
                    "correctAnswer": "Roma",
                    "type": "QUIZ"
                }
                """;
        } else if ("TRUE_FALSE".equalsIgnoreCase(type)) {
            return """
                {
                    "question": "Il Sole è una stella.",
                    "options": ["VERO", "FALSO"],
                    "correctAnswer": "VERO",
                    "type": "TRUE_FALSE"
                }
                """;
        } else if ("CHRONO".equalsIgnoreCase(type)) {
            return """
                {
                    "question": "In che anno è caduto il muro di Berlino?",
                    "options": null,
                    "correctAnswer": "1989",
                    "type": "CHRONO"
                }
                """;
        }
        return "{}";
    }
}