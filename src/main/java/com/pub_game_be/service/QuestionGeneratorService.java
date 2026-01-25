package com.pub_game_be.service;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class QuestionGeneratorService {

    private final String GROQ_API_URL = "https://api.groq.com/openai/v1/chat/completions";

    @Value("${groq.api.key}")
    private String apiKey;

    private final Set<String> recentCelebrities = ConcurrentHashMap.newKeySet();
    private final int MAX_RECENT = 20;

    public String generateQuestionJson(String category, String type, String difficulty) {
        RestTemplate restTemplate = new RestTemplate();

        String difficultyContext = switch (difficulty.toLowerCase()) {
            case "facile" -> "Usa personaggi/domande molto popolari, quasi ovvi.";
            case "medio" -> "Usa personaggi/domande di buona fama, ma non iconici.";
            case "difficile" -> "Sii specifico. Usa personaggi/dettagli meno noti.";
            default -> "Difficoltà bilanciata.";
        };

        String prompt;

        if ("IMAGE_BLUR".equalsIgnoreCase(type)) {
            String recentList = recentCelebrities.isEmpty()
                    ? "nessuno"
                    : String.join(", ", recentCelebrities);

            prompt = "Sei il presentatore di un quiz televisivo basato sul riconoscimento visivo.\n" +
                    "CATEGORIA: " + category + "\n" +
                    "LIVELLO: " + difficulty + " (" + difficultyContext + ")\n\n" +
                    "REGOLE DI SELEZIONE:\n" +
                    "1. Scegli una celebrità diversa ogni volta\n" +
                    "2. NON usare questi (già usciti): " + recentList + "\n" +
                    "Rispondi SOLO con JSON valido (NO markdown, NO testo extra), con campo imageUrl diretto a Wikimedia Commons (upload.wikimedia.org) e .jpg.\n" +
                    "{ \"question\": \"Chi è questa persona?\", \"correctAnswer\": \"Nome Completo\", \"imageUrl\": \"URL_IMMAGINE\", \"type\": \"IMAGE_BLUR\", \"options\": null }";
        } else {
            prompt = "Sei il presentatore di un quiz televisivo. Genera una domanda per la categoria " + category +
                    " di tipo " + type + ".\nLivello: " + difficulty + " (" + difficultyContext + ")\nRispondi SOLO con JSON valido.";
        }

        Map<String, Object> request = new HashMap<>();
        request.put("model", "llama-3.3-70b-versatile");
        request.put("messages", List.of(Map.of("role", "user", "content", prompt)));
        request.put("temperature", "IMAGE_BLUR".equalsIgnoreCase(type) ? 1.3 : 0.7);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(GROQ_API_URL, entity, String.class);
            String rawContent = parseJsonResponse(response.getBody());
            String cleanedJson = cleanAiJson(rawContent);

            JSONObject jsonObj = new JSONObject(cleanedJson);

            // Risolvi Special:FilePath → URL diretto upload.wikimedia.org
            if ("IMAGE_BLUR".equalsIgnoreCase(type) && jsonObj.has("imageUrl")) {
                String imageUrl = jsonObj.getString("imageUrl");
                if (imageUrl.contains("Special:FilePath")) {
                    imageUrl = resolveWikimediaImage(imageUrl);
                    jsonObj.put("imageUrl", imageUrl);
                }
            }

            // Aggiorna cache celebrità
            if ("IMAGE_BLUR".equalsIgnoreCase(type) && jsonObj.has("correctAnswer")) {
                String celebrity = jsonObj.getString("correctAnswer");
                addToRecentCelebrities(celebrity);
            }

            return jsonObj.toString();

        } catch (Exception e) {
            e.printStackTrace();
            return getFallbackJson(type);
        }
    }

    private void addToRecentCelebrities(String celebrity) {
        recentCelebrities.add(celebrity);
        if (recentCelebrities.size() > MAX_RECENT) {
            Iterator<String> it = recentCelebrities.iterator();
            if (it.hasNext()) {
                it.next();
                it.remove();
            }
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
        return (firstBrace >= 0 && lastBrace >= 0) ? content.substring(firstBrace, lastBrace + 1).trim() : content.trim();
    }

    private String parseJsonResponse(String responseBody) {
        JSONObject json = new JSONObject(responseBody);
        return json.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content");
    }

    private String getFallbackJson(String type) {
        if ("IMAGE_BLUR".equalsIgnoreCase(type)) {
            return """
                    {
                        "question": "Chi è questa persona?",
                        "correctAnswer": "Tom Hanks",
                        "imageUrl": "https://upload.wikimedia.org/wikipedia/commons/4/44/Tom_Hanks.jpg",
                        "type": "IMAGE_BLUR",
                        "options": null
                    }
                    """;
        }
        return "{}";
    }

    private String resolveWikimediaImage(String filePathUrl) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "PubGameBot/1.0");
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<Void> response = restTemplate.exchange(filePathUrl, HttpMethod.HEAD, entity, Void.class);
            URI finalUri = response.getHeaders().getLocation();
            return finalUri != null ? finalUri.toString() : filePathUrl;
        } catch (Exception e) {
            System.err.println("Errore risoluzione immagine: " + e.getMessage());
            return filePathUrl;
        }
    }
}
