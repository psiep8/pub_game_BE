package com.pub_game_be.service;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class QuestionGeneratorService {

    private final String GROQ_API_URL = "https://api.groq.com/openai/v1/chat/completions";

    @Value("${groq.api.key}")
    private String apiKey;

    private final CelebrityImageService imageService;

    // Cache per evitare ripetizioni
    private final Set<String> recentCelebrities = ConcurrentHashMap.newKeySet();
    private final int MAX_RECENT = 20;

    public QuestionGeneratorService(CelebrityImageService imageService) {
        this.imageService = imageService;
    } // Ricorda ultimi 20 personaggi

    public String generateQuestionJson(String category, String type, String difficulty) {
        RestTemplate restTemplate = new RestTemplate();

        String difficultyContext = switch (difficulty.toLowerCase()) {
            case "facile" -> "Usa personaggi/domande molto popolari, quasi ovvi.";
            case "medio" -> "Usa personaggi/domande di buona fama, ma non iconici.";
            case "difficile" -> "Sii specifico. Usa personaggi/dettagli meno noti.";
            default -> "Difficoltà bilanciata.";
        };

        String prompt;

        // ========== IMAGE_BLUR: Logica speciale ==========
        if ("IMAGE_BLUR".equalsIgnoreCase(type)) {
            // Lista dei personaggi recenti per evitare ripetizioni
            String recentList = recentCelebrities.isEmpty()
                    ? "nessuno"
                    : String.join(", ", recentCelebrities);

            prompt = String.format(
                    "Sei il presentatore di un quiz televisivo basato sul riconoscimento visivo.\n" +
                            "CATEGORIA: %s\n" +
                            "LIVELLO: %s (%s)\n\n" +
                            "REGOLE DI SELEZIONE:\n" +
                            "1. Scegli una celebrità DIVERSA ogni volta\n" +
                            "2. NON USARE QUESTI (già usciti): %s\n" +
                            "3. PREFERENZA per personaggi ITALIANI (60%% italiani, 40%% internazionali)\n" +
                            "4. Varia tra: attori, cantanti, sportivi, registi, conduttori TV\n\n" +
                            "ESEMPI ITALIANI livello MEDIO:\n" +
                            "- Cinema: Alessandro Borghi, Luca Marinelli, Jasmine Trinca, Valeria Golino\n" +
                            "- Musica: Mahmood, Blanco, Elodie, Annalisa\n" +
                            "- Sport: Jannik Sinner, Gianmarco Tamberi, Federica Pellegrini\n" +
                            "- TV: Alessandro Cattelan, Geppi Cucciari, Nino Frassica\n\n" +
                            "ESEMPI INTERNAZIONALI livello MEDIO:\n" +
                            "- Jake Gyllenhaal, Margot Robbie, Zendaya, Pedro Pascal, Oscar Isaac\n\n" +
                            "Rispondi SOLO con JSON valido (NO markdown, NO testo extra):\n" +
                            "{\n" +
                            "  \"question\": \"Chi è questa persona?\",\n" +
                            "  \"correctAnswer\": \"Nome Completo\",\n" +
                            "  \"imageUrl\": \"URL_IMMAGINE\",\n" +
                            "  \"type\": \"IMAGE_BLUR\",\n" +
                            "  \"options\": null\n" +
                            "}\n\n" +
                            "FORMATO URL OBBLIGATORIO:\n" +
                            "Per personaggi ITALIANI:\n" +
                            "- https://it.wikipedia.org/wiki/Special:FilePath/Nome_Cognome\n" +
                            "Esempio: https://it.wikipedia.org/wiki/Special:FilePath/Alessandro_Borghi\n\n" +
                            "Per personaggi INTERNAZIONALI:\n" +
                            "- https://en.wikipedia.org/wiki/Special:FilePath/Nome_Cognome\n" +
                            "Esempio: https://en.wikipedia.org/wiki/Special:FilePath/Jake_Gyllenhaal\n\n" +
                            "IMPORTANTE:\n" +
                            "- Sostituisci spazi con underscore (_)\n" +
                            "- NON aggiungere estensioni (.jpg, .png)\n" +
                            "- Usa il nome ESATTO della pagina Wikipedia",
                    category, difficulty, difficultyContext, recentList
            );
        }
        // ========== ALTRI TIPI: Logica normale ==========
        else {
            prompt = String.format(
                    "Sei il presentatore di un quiz televisivo. Genera una domanda per la categoria %s di tipo %s.\n" +
                            "LIVELLO DI DIFFICOLTÀ: %s (%s).\n" +
                            "Rispondi SOLO con JSON valido (NO markdown):\n" +
                            "- question: testo della domanda\n" +
                            "- options: array di 4 stringhe (QUIZ), ['VERO','FALSO'] (TRUE_FALSE), null (CHRONO)\n" +
                            "- correctAnswer: risposta esatta o anno (CHRONO)\n" +
                            "- type: %s",
                    category, type, difficulty, difficultyContext, type
            );
        }

        Map<String, Object> request = new HashMap<>();
        request.put("model", "llama-3.3-70b-versatile");
        request.put("messages", List.of(Map.of("role", "user", "content", prompt)));

        // Temperature alta per IMAGE_BLUR = più varietà
        if ("IMAGE_BLUR".equalsIgnoreCase(type)) {
            request.put("temperature", 1.3);
        } else {
            request.put("temperature", 0.7);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(GROQ_API_URL, entity, String.class);
            String rawContent = parseJsonResponse(response.getBody());
            String cleanedJson = cleanAiJson(rawContent);

            System.out.println("=== AI Response for " + type + " ===");
            System.out.println("RAW: " + rawContent);
            System.out.println("CLEANED: " + cleanedJson);

            // Valida JSON
            try {
                JSONObject jsonObj = new JSONObject(cleanedJson);

                // Se IMAGE_BLUR, sostituisci imageUrl con Unsplash
                if ("IMAGE_BLUR".equalsIgnoreCase(type) && jsonObj.has("correctAnswer")) {
                    String celebrity = jsonObj.getString("correctAnswer");

                    // Genera URL immagine affidabile
                    String imageUrl = imageService.getReliableImageUrl(celebrity);
                    jsonObj.put("imageUrl", imageUrl);

                    addToRecentCelebrities(celebrity);
                    System.out.println("✅ Celebrità: " + celebrity);
                    System.out.println("🖼️ Immagine URL: " + imageUrl);
                    System.out.println("📋 Cache: " + recentCelebrities);

                    cleanedJson = jsonObj.toString();
                }

                System.out.println("===================================");
                return cleanedJson;

            } catch (Exception parseEx) {
                System.err.println("❌ JSON non valido: " + parseEx.getMessage());
                return getFallbackJson(type);
            }

        } catch (Exception e) {
            System.err.println("❌ Errore chiamata Groq: " + e.getMessage());
            e.printStackTrace();
            return getFallbackJson(type);
        }
    }

    /**
     * Aggiunge una celebrità alla cache evitando ripetizioni
     */
    private void addToRecentCelebrities(String celebrity) {
        recentCelebrities.add(celebrity);

        // Mantieni solo gli ultimi MAX_RECENT
        if (recentCelebrities.size() > MAX_RECENT) {
            // Rimuovi il più vecchio (implementazione semplificata)
            Iterator<String> iterator = recentCelebrities.iterator();
            if (iterator.hasNext()) {
                iterator.next();
                iterator.remove();
            }
        }
    }

    private String cleanAiJson(String content) {
        if (content == null) return "{}";

        // Rimuovi markdown
        if (content.contains("```json")) {
            content = content.substring(content.indexOf("```json") + 7);
            content = content.substring(0, content.lastIndexOf("```"));
        } else if (content.contains("```")) {
            content = content.substring(content.indexOf("```") + 3);
            content = content.substring(0, content.lastIndexOf("```"));
        }

        // Estrai JSON
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

    private String getFallbackJson(String type) {
        if ("IMAGE_BLUR".equalsIgnoreCase(type)) {
            // Fallback italiani per varietà
            String[] italianFallbacks = {
                    """
                {
                    "question": "Chi è questa persona?",
                    "correctAnswer": "Alessandro Borghi",
                    "imageUrl": "https://it.wikipedia.org/wiki/Special:FilePath/Alessandro_Borghi",
                    "type": "IMAGE_BLUR",
                    "options": null
                }
                """,
                    """
                {
                    "question": "Chi è questa persona?",
                    "correctAnswer": "Elodie",
                    "imageUrl": "https://it.wikipedia.org/wiki/Special:FilePath/Elodie",
                    "type": "IMAGE_BLUR",
                    "options": null
                }
                """,
                    """
                {
                    "question": "Chi è questa persona?",
                    "correctAnswer": "Jannik Sinner",
                    "imageUrl": "https://it.wikipedia.org/wiki/Special:FilePath/Jannik_Sinner",
                    "type": "IMAGE_BLUR",
                    "options": null
                }
                """
            };
            return italianFallbacks[new Random().nextInt(italianFallbacks.length)];
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