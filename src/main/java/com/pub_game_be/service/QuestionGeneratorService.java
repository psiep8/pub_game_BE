package com.pub_game_be.service;

import com.pub_game_be.dto.MusicTrackDto;
import org.json.JSONArray;
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

    @Value("${groq.api.model}")
    private String model;

    @Value("${groq.api.temperature}")
    private double defaultTemperature;

    @Value("${groq.api.image-blur-temperature}")
    private double imageBlurTemperature;

    private final RestTemplate restTemplate;
    private final TMDBImageService tmdbImageService;
    private final AppleMusicCuratorService appleMusicCuratorService;
    private final com.pub_game_be.repository.CategoryRepository categoryRepository;

    private final Set<String> recentCelebrities = ConcurrentHashMap.newKeySet();
    private final int MAX_RECENT = 20;

    public QuestionGeneratorService(RestTemplate restTemplate,
            TMDBImageService tmdbImageService,
            AppleMusicCuratorService appleMusicCuratorService,
            com.pub_game_be.repository.CategoryRepository categoryRepository) {
        this.restTemplate = restTemplate;
        this.tmdbImageService = tmdbImageService;
        this.appleMusicCuratorService = appleMusicCuratorService;
        this.categoryRepository = categoryRepository;
    }

    public String generateQuestionJson(String category, String type, String difficulty) {

        if ("ROULETTE".equalsIgnoreCase(type)) {
            String[] colors = { "ROSSO", "NERO", "VERDE", "BLU", "GIALLO", "BIANCO" };
            String winningColor = colors[new Random().nextInt(colors.length)];

            JSONObject response = new JSONObject();
            response.put("type", "ROULETTE");
            response.put("question", "Scegli un colore!");
            response.put("correctAnswer", winningColor);
            response.put("options", new JSONArray(colors));
            response.put("payload", JSONObject.NULL);

            return response.toString();
        }

        if ("WHEEL_OF_FORTUNE".equalsIgnoreCase(type)
                || "WHEEL_FORTUNE".equalsIgnoreCase(type)
                || "PROVERB".equalsIgnoreCase(type)) {
            String proverb = pickRandomProverb();
            return proverb;
        }

        if ("MUSIC".equalsIgnoreCase(type)) {
            return generateMusicQuestion();
        }

        String difficultyContext = switch (difficulty.toLowerCase()) {
            case "facile" -> "Usa personaggi/domande molto popolari, quasi ovvi.";
            case "medio" -> "Usa personaggi/domande di buona fama, ma non iconici.";
            case "difficile" -> "Sii specifico. Usa personaggi/dettagli meno noti.";
            default -> "Difficoltà bilanciata.";
        };

        String prompt;
        if ("SCREAM_RACE".equalsIgnoreCase(type)) {
            return """
                    {
                        "type": "SCREAM_RACE",
                        "duration": 30,
                        "instructions": "Urla nel microfono per far avanzare la tua squadra!"
                    }
                    """;
        }

        if ("IMAGE_BLUR".equalsIgnoreCase(type)) {
            String recentList = recentCelebrities.isEmpty()
                    ? "nessuno"
                    : String.join(", ", recentCelebrities);

            prompt = String.format(
                    "Sei il presentatore di un quiz televisivo.\n" +
                            "CATEGORIA: %s\n" +
                            "LIVELLO: %s (%s)\n\n" +
                            "⚠️ REGOLE CRITICHE - SOLO CELEBRITÀ ULTRA-FAMOSE:\n" +
                            "1. USA SOLO personaggi ICONICI conosciuti da TUTTI\n" +
                            "2. NON usare personaggi di nicchia, emergenti o poco noti\n" +
                            "3. PREFERENZA ASSOLUTA per:\n" +
                            "   - Cinema: attori/attrici con Oscar, Golden Globe, film blockbuster\n" +
                            "   - Musica: artisti multi-platino, premi Grammy/MTV\n" +
                            "   - Sport: campioni mondiali/olimpici, leggende dello sport\n" +
                            "   - TV: conduttori storici prime-time\n\n" +
                            "4. NON USARE QUESTI (già usciti): %s\n\n" +
                            "ESEMPI ITALIANI ACCETTABILI:\n" +
                            "- Cinema: Sophia Loren, Roberto Benigni, Monica Bellucci, Pierfrancesco Favino\n" +
                            "- Musica: Laura Pausini, Eros Ramazzotti, Vasco Rossi, Ligabue, Zucchero\n" +
                            "- Sport: Francesco Totti, Alessandro Del Piero, Valentino Rossi\n" +
                            "- TV: Gerry Scotti, Maria De Filippi, Paolo Bonolis\n\n" +
                            "ESEMPI INTERNAZIONALI ACCETTABILI:\n" +
                            "- Cinema: Tom Cruise, Brad Pitt, Leonardo DiCaprio, Meryl Streep, Angelina Jolie\n" +
                            "- Musica: Michael Jackson, Madonna, Beyoncé, Ed Sheeran, Taylor Swift\n" +
                            "- Sport: Cristiano Ronaldo, Lionel Messi, LeBron James, Roger Federer\n\n" +
                            "⚠️ ESEMPI DA EVITARE (troppo di nicchia):\n" +
                            "- Attori di serie TV minori o film indie\n" +
                            "- Cantanti emergenti o con 1-2 hit\n" +
                            "- Personaggi social/influencer\n" +
                            "- Sportivi senza titoli mondiali/olimpici\n\n" +
                            "Rispondi SOLO con JSON:\n" +
                            "{\n" +
                            "  \"question\": \"Chi è questa persona?\",\n" +
                            "  \"correctAnswer\": \"Nome Completo Esatto\",\n" +
                            "  \"type\": \"IMAGE_BLUR\",\n" +
                            "  \"options\": null\n" +
                            "}",
                    category, difficulty, difficultyContext, recentList);
        }
        if ("TRUE_FALSE".equalsIgnoreCase(type)) {
            boolean shouldBeTrue = new Random().nextBoolean();
            prompt = String.format(
                    "Sei il presentatore di un quiz televisivo.\n" +
                            "CATEGORIA: %s\n" +
                            "LIVELLO: %s (%s)\n\n" +
                            "Genera UNA domanda VERO/FALSO.\n\n" +
                            "⚠️ REGOLA CRITICA:\n" +
                            "La risposta corretta DEVE essere: %s\n\n" +
                            "REGOLE:\n" +
                            "1. La domanda deve avere risposta OGGETTIVA\n" +
                            "2. La risposta deve essere VERIFICABILE\n" +
                            "3. 'options' = [\"VERO\", \"FALSO\"]\n" +
                            "4. 'correctAnswer' = \"%s\"\n" +
                            "5. NON inventare fatti\n" +
                            "6. Se la risposta è \"FALSO\", DEVI aggiungere 'explanation'\n\n" +
                            "ESEMPIO VERO:\n" +
                            "{\n" +
                            "  \"question\": \"Il Sole è una stella.\",\n" +
                            "  \"options\": [\"VERO\", \"FALSO\"],\n" +
                            "  \"correctAnswer\": \"VERO\",\n" +
                            "  \"type\": \"TRUE_FALSE\"\n" +
                            "}\n\n" +
                            "ESEMPIO FALSO:\n" +
                            "{\n" +
                            "  \"question\": \"La Torre di Pisa si trova a Firenze.\",\n" +
                            "  \"options\": [\"VERO\", \"FALSO\"],\n" +
                            "  \"correctAnswer\": \"FALSO\",\n" +
                            "  \"explanation\": \"La Torre di Pisa si trova a Pisa, non a Firenze.\",\n" +
                            "  \"type\": \"TRUE_FALSE\"\n" +
                            "}\n\n" +
                            "Rispondi SOLO con JSON valido (NO markdown).",
                    category,
                    difficulty,
                    difficultyContext,
                    shouldBeTrue ? "VERO" : "FALSO",
                    shouldBeTrue ? "VERO" : "FALSO");
        } else if ("QUIZ".equalsIgnoreCase(type)) {
            prompt = String.format(
                    "Sei il presentatore di un quiz televisivo.\n" +
                            "CATEGORIA: %s\n" +
                            "LIVELLO: %s (%s)\n\n" +
                            "Genera UNA domanda di cultura generale.\n\n" +
                            "REGOLE RIGIDE:\n" +
                            "1. La domanda deve avere 4 opzioni di risposta\n" +
                            "2. UNA SOLA risposta deve essere corretta\n" +
                            "3. Le altre 3 devono essere PLAUSIBILI ma SBAGLIATE\n" +
                            "4. La 'correctAnswer' DEVE essere IDENTICA a una delle 4 opzioni\n" +
                            "5. NON inventare fatti o date inesistenti\n\n" +
                            "ESEMPIO VALIDO:\n" +
                            "{\n" +
                            "  \"question\": \"Qual è la capitale della Francia?\",\n" +
                            "  \"options\": [\"Parigi\", \"Londra\", \"Berlino\", \"Madrid\"],\n" +
                            "  \"correctAnswer\": \"Parigi\",\n" +
                            "  \"type\": \"QUIZ\"\n" +
                            "}\n\n" +
                            "Rispondi SOLO con JSON valido (NO markdown, NO testo extra).\n" +
                            "IMPORTANTE: Verifica che 'correctAnswer' sia ESATTAMENTE uguale a una delle options!",
                    category, difficulty, difficultyContext);
        } else if ("CHRONO".equalsIgnoreCase(type)) {
            prompt = String.format(
                    "Sei il presentatore di un quiz televisivo.\n" +
                            "CATEGORIA: %s\n" +
                            "LIVELLO: %s (%s)\n\n" +
                            "Genera UNA domanda su un ANNO storico.\n\n" +
                            "REGOLE:\n" +
                            "1. La domanda deve chiedere l'anno di un evento storico\n" +
                            "2. L'anno deve essere verificabile\n" +
                            "3. 'correctAnswer' deve essere un anno (es: \"1989\")\n" +
                            "4. 'options' deve essere null\n\n" +
                            "ESEMPIO:\n" +
                            "{\n" +
                            "  \"question\": \"In che anno è caduto il muro di Berlino?\",\n" +
                            "  \"options\": null,\n" +
                            "  \"correctAnswer\": \"1989\",\n" +
                            "  \"type\": \"CHRONO\"\n" +
                            "}\n\n" +
                            "Rispondi SOLO con JSON valido (NO markdown).",
                    category, difficulty, difficultyContext);
        }
        if ("ARENA".equalsIgnoreCase(type)) {
            prompt = String.format(
                    "Sei il presentatore di un quiz televisivo.\n" +
                            "Genera una lista di 40 domande miste per la modalità ARENA (Battle Royale).\n" +
                            "⚠️ REGOLE RIGIDE:\n" +
                            "1. Alterna diverse categorie (Scienza, Storia, Sport, Cinema, Geografia, Curiosità, ecc.).\n"
                            +
                            "2. Usa un mix di tipi: 70%% tipo 'QUIZ' (4 opzioni) e 30%% tipo 'TRUE_FALSE' (2 opzioni: VERO, FALSO).\n"
                            +
                            "3. Le prime 20 domande devono essere FACILI, le restanti 20 di DIFFICOLTÀ MEDIA.\n" +
                            "4. Ogni oggetto JSON deve avere: \"question\", \"options\" (array di stringhe), \"correctAnswer\" (valore esatto presente in options), \"difficulty\" (\"easy\" o \"medium\"), \"category\" (nome categoria).\n"
                            +
                            "5. Per le domande TRUE_FALSE, 'options' deve essere [\"VERO\", \"FALSO\"].\n" +
                            "6. Rispondi SOLO con un array JSON valido, senza testo extra.\n\n" +
                            "Esempio formato:\n" +
                            "[\n" +
                            "  { \"question\": \"...\", \"options\": [\"A\",\"B\",\"C\",\"D\"], \"correctAnswer\": \"A\", \"difficulty\": \"easy\", \"category\": \"Storia\" },\n"
                            +
                            "  { \"question\": \"...\", \"options\": [\"VERO\",\"FALSO\"], \"correctAnswer\": \"VERO\", \"difficulty\": \"easy\", \"category\": \"Scienza\" }\n"
                            +
                            "]");

            Map<String, Object> request = new HashMap<>();
            request.put("model", model);
            request.put("messages", List.of(Map.of("role", "user", "content", prompt)));
            request.put("temperature", defaultTemperature);

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(apiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

            try {
                ResponseEntity<String> response = restTemplate.postForEntity(GROQ_API_URL, entity, String.class);
                String rawContent = parseJsonResponse(response.getBody());
                String cleanedJson = cleanAiJson(rawContent);

                JSONObject wrapper = new JSONObject();
                wrapper.put("type", "ARENA");
                wrapper.put("questions", new JSONArray()); // Return empty array to TV, devices will fetch individually
                return wrapper.toString();

            } catch (Exception e) {
                return getFallbackJson("ARENA");
            }

        } else if ("ONE_VS_ONE".equalsIgnoreCase(type) || "1VS1".equalsIgnoreCase(type)) {
            prompt = String.format(
                    "Sei il presentatore di un quiz televisivo.\n" +
                            "CATEGORIA: %s\n" +
                            "LIVELLO: %s (%s)\n\n" +
                            "Genera UNA domanda QUIZ per una sfida 1 contro 1.\n\n" +
                            "REGOLE:\n" +
                            "1. La domanda deve avere 4 opzioni\n" +
                            "2. UNA SOLA risposta corretta\n" +
                            "3. Domanda di MEDIA difficoltà (non troppo facile, non impossibile)\n" +
                            "4. La 'correctAnswer' DEVE essere in 'options'\n\n" +
                            "Rispondi SOLO con JSON valido:\n" +
                            "{\n" +
                            "  \"question\": \"...\",\n" +
                            "  \"options\": [\"A\", \"B\", \"C\", \"D\"],\n" +
                            "  \"correctAnswer\": \"...\",\n" +
                            "  \"type\": \"ONE_VS_ONE\"\n" +
                            "}",
                    category, difficulty, difficultyContext);
        } else {
            prompt = String.format(
                    "Sei il presentatore di un quiz televisivo. Genera una domanda per la categoria %s di tipo %s.\n" +
                            "LIVELLO DI DIFFICOLTÀ: %s (%s).\n" +
                            "Rispondi SOLO con JSON valido (NO markdown):\n" +
                            "- question: testo della domanda\n" +
                            "- options: array di 4 stringhe (QUIZ), ['VERO','FALSO'] (TRUE_FALSE), null (CHRONO)\n" +
                            "- correctAnswer: risposta esatta o anno (CHRONO)\n" +
                            "- type: %s",
                    category, type, difficulty, difficultyContext, type);
        }

        Map<String, Object> request = new HashMap<>();
        request.put("model", model);
        request.put("messages", List.of(Map.of("role", "user", "content", prompt)));

        if ("IMAGE_BLUR".equalsIgnoreCase(type)) {
            request.put("temperature", imageBlurTemperature);
        } else {
            request.put("temperature", defaultTemperature);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(GROQ_API_URL, entity, String.class);
            String rawContent = parseJsonResponse(response.getBody());
            String cleanedJson = cleanAiJson(rawContent);

            try {
                JSONObject jsonObj = new JSONObject(cleanedJson);

                if ("QUIZ".equalsIgnoreCase(type) || "TRUE_FALSE".equalsIgnoreCase(type)) {
                    if (!validateQuizJson(jsonObj, type)) {
                        return getFallbackJson(type);
                    }
                }

                if ("IMAGE_BLUR".equalsIgnoreCase(type) && jsonObj.has("correctAnswer")) {
                    String celebrity = jsonObj.getString("correctAnswer");

                    String imageUrl = tmdbImageService.getCelebrityImageUrl(celebrity);

                    if (imageUrl != null && !imageUrl.isEmpty()) {
                        jsonObj.put("imageUrl", imageUrl);
                        addToRecentCelebrities(celebrity);

                    } else {
                        return generateQuestionJson(category, type, difficulty);
                    }

                    cleanedJson = jsonObj.toString();
                }

                return cleanedJson;

            } catch (Exception parseEx) {
                return getFallbackJson(type);
            }

        } catch (Exception e) {
            return getFallbackJson(type);
        }
    }

    private boolean validateQuizJson(JSONObject jsonObj, String type) {
        try {
            if (!jsonObj.has("correctAnswer") || !jsonObj.has("options")) {
                return false;
            }

            String correctAnswer = jsonObj.getString("correctAnswer");
            JSONArray options = jsonObj.getJSONArray("options");

            if ("TRUE_FALSE".equalsIgnoreCase(type)) {
                if (options.length() != 2) {
                    return false;
                }

                boolean hasVero = false;
                boolean hasFalso = false;
                for (int i = 0; i < options.length(); i++) {
                    String opt = options.getString(i);
                    if ("VERO".equals(opt))
                        hasVero = true;
                    if ("FALSO".equals(opt))
                        hasFalso = true;
                }

                if (!hasVero || !hasFalso) {
                    return false;
                }

                if (!"VERO".equals(correctAnswer) && !"FALSO".equals(correctAnswer)) {
                    return false;
                }

                return true;
            }

            boolean found = false;
            for (int i = 0; i < options.length(); i++) {
                if (options.getString(i).equals(correctAnswer)) {
                    found = true;
                    break;
                }
            }

            if (!found) {
                return false;
            }

            return true;

        } catch (Exception e) {
            return false;
        }
    }

    public String generateSingleArenaQuestion(String category) {
        String prompt = "Sei il presentatore di un quiz televisivo.\n" +
                "Genera UNA singola domanda casuale per la modalità ARENA (Battle Royale).\n" +
                "⚠️ REGOLE RIGIDE:\n" +
                "1. La categoria della domanda DEVE essere la seguente: " + category + ".\n" +
                "2. Scegli casualmente il tipo: 70% probabilità 'QUIZ' (4 opzioni) o 30% probabilità 'TRUE_FALSE' (2 opzioni: VERO, FALSO).\n"
                +
                "3. Difficoltà: Facile o Media.\n" +
                "4. L'oggetto JSON deve avere ESATTAMENTE: \"question\" (testo), \"options\" (array di stringhe), \"correctAnswer\" (valore esatto), \"difficulty\", \"category\".\n"
                +
                "5. Per TRUE_FALSE, 'options' deve essere ESATTAMENTE [\"VERO\", \"FALSO\"].\n" +
                "6. Rispondi SOLO con un oggetto JSON valido, senza list, senza array attorno, nessun testo extra.\n\n"
                +
                "Esempio QUIZ:\n" +
                "{ \"question\": \"...\", \"options\": [\"A\",\"B\",\"C\",\"D\"], \"correctAnswer\": \"A\", \"difficulty\": \"easy\", \"category\": \"Storia\" }\n\n"
                +
                "Esempio TRUE_FALSE:\n" +
                "{ \"question\": \"...\", \"options\": [\"VERO\",\"FALSO\"], \"correctAnswer\": \"VERO\", \"difficulty\": \"medium\", \"category\": \"Scienza\" }";

        Map<String, Object> request = new HashMap<>();
        request.put("model", model);
        request.put("messages", List.of(Map.of("role", "user", "content", prompt)));
        request.put("temperature", defaultTemperature);
        request.put("response_format", Map.of("type", "json_object")); // Force JSON object

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(GROQ_API_URL, entity, String.class);
            String rawContent = parseJsonResponse(response.getBody());
            String cleanedJson = cleanAiJson(rawContent);

            JSONObject jsonObj = new JSONObject(cleanedJson);

            // Validate it
            if (!jsonObj.has("correctAnswer") || !jsonObj.has("options") || !jsonObj.has("question")) {
                System.err.println("❌ Formato JSON AI non valido: " + cleanedJson);
                return getSingleFallbackArenaQuestion();
            }

            return jsonObj.toString();

        } catch (Exception e) {
            System.err.println("❌ Eccezione durante la generazione AI Arena: " + e.getMessage());
            return getSingleFallbackArenaQuestion();
        }
    }

    private String getSingleFallbackArenaQuestion() {
        String[] safeFallbacks = {
                "{ \"question\": \"Quanto fa 5 + 5?\", \"options\": [\"8\", \"10\", \"12\", \"15\"], \"correctAnswer\": \"10\", \"difficulty\": \"easy\", \"category\": \"Matematica\" }",
                "{ \"question\": \"Qual è la capitale d'Italia?\", \"options\": [\"Milano\", \"Roma\", \"Napoli\", \"Torino\"], \"correctAnswer\": \"Roma\", \"difficulty\": \"easy\", \"category\": \"Geografia\" }",
                "{ \"question\": \"Qual è il fiume più lungo del mondo?\", \"options\": [\"Nilo\", \"Rio delle Amazzoni\", \"Gange\", \"Mississippi\"], \"correctAnswer\": \"Rio delle Amazzoni\", \"difficulty\": \"medium\", \"category\": \"Geografia\" }",
                "{ \"question\": \"Chi ha dipinto la Gioconda?\", \"options\": [\"Michelangelo\", \"Raffaello\", \"Munch\", \"Leonardo Da Vinci\"], \"correctAnswer\": \"Leonardo Da Vinci\", \"difficulty\": \"easy\", \"category\": \"Arte\" }",
                "{ \"question\": \"Qual è il pianeta più grande del sistema solare?\", \"options\": [\"Giove\", \"Saturno\", \"Terra\", \"Marte\"], \"correctAnswer\": \"Giove\", \"difficulty\": \"easy\", \"category\": \"Scienza\" }",
                "{ \"question\": \"La Terra è piatta?\", \"options\": [\"VERO\", \"FALSO\"], \"correctAnswer\": \"FALSO\", \"difficulty\": \"easy\", \"category\": \"Geografia\" }",
                "{ \"question\": \"L'acqua bolle a 100 gradi Celsius?\", \"options\": [\"VERO\", \"FALSO\"], \"correctAnswer\": \"VERO\", \"difficulty\": \"easy\", \"category\": \"Scienza\" }",
                "{ \"question\": \"Roma è la capitale della Francia?\", \"options\": [\"VERO\", \"FALSO\"], \"correctAnswer\": \"FALSO\", \"difficulty\": \"easy\", \"category\": \"Geografia\" }",
                "{ \"question\": \"Il sole è una stella?\", \"options\": [\"VERO\", \"FALSO\"], \"correctAnswer\": \"VERO\", \"difficulty\": \"easy\", \"category\": \"Scienza\" }",
                "{ \"question\": \"In che anno è iniziata la Prima Guerra Mondiale?\", \"options\": [\"1914\", \"1918\", \"1939\", \"1945\"], \"correctAnswer\": \"1914\", \"difficulty\": \"medium\", \"category\": \"Storia\" }",
                "{ \"question\": \"Chi ha scritto la Divina Commedia?\", \"options\": [\"Boccaccio\", \"Petrarca\", \"Dante Alighieri\", \"Machiavelli\"], \"correctAnswer\": \"Dante Alighieri\", \"difficulty\": \"easy\", \"category\": \"Letteratura\" }",
                "{ \"question\": \"Quale animale è conosciuto come il miglior amico dell'uomo?\", \"options\": [\"Gatto\", \"Cane\", \"Cavallo\", \"Delfino\"], \"correctAnswer\": \"Cane\", \"difficulty\": \"easy\", \"category\": \"Natura\" }",
                "{ \"question\": \"Il ragno è un insetto?\", \"options\": [\"VERO\", \"FALSO\"], \"correctAnswer\": \"FALSO\", \"difficulty\": \"medium\", \"category\": \"Natura\" }",
                "{ \"question\": \"Qual è lo sport più popolare al mondo?\", \"options\": [\"Basket\", \"Tennis\", \"Calcio\", \"Rugby\"], \"correctAnswer\": \"Calcio\", \"difficulty\": \"easy\", \"category\": \"Sport\" }",
                "{ \"question\": \"L'Uomo Ragno fa parte della DC Comics?\", \"options\": [\"VERO\", \"FALSO\"], \"correctAnswer\": \"FALSO\", \"difficulty\": \"medium\", \"category\": \"Fumetti\" }",
                "{ \"question\": \"Quanti giorni ci sono in un anno bisestile?\", \"options\": [\"364\", \"365\", \"366\", \"367\"], \"correctAnswer\": \"366\", \"difficulty\": \"easy\", \"category\": \"Curiosità\" }"
        };
        return safeFallbacks[new Random().nextInt(safeFallbacks.length)];
    }

    private void addToRecentCelebrities(String celebrity) {
        recentCelebrities.add(celebrity);

        if (recentCelebrities.size() > MAX_RECENT) {
            Iterator<String> iterator = recentCelebrities.iterator();
            if (iterator.hasNext()) {
                iterator.next();
                iterator.remove();
            }
        }
    }

    private String cleanAiJson(String content) {
        if (content == null)
            return "{}";

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

    private String getFallbackJson(String type) {
        if ("IMAGE_BLUR".equalsIgnoreCase(type)) {
            String[] safeFallbacks = {
                    """
                            {
                                "question": "Chi è questa persona?",
                                "correctAnswer": "Leonardo DiCaprio",
                                "type": "IMAGE_BLUR",
                                "options": null
                            }
                            """,
                    """
                            {
                                "question": "Chi è questa persona?",
                                "correctAnswer": "Brad Pitt",
                                "type": "IMAGE_BLUR",
                                "options": null
                            }
                            """,
                    """
                            {
                                "question": "Chi è questa persona?",
                                "correctAnswer": "Tom Hanks",
                                "type": "IMAGE_BLUR",
                                "options": null
                            }
                            """
            };

            String fallbackJson = safeFallbacks[new Random().nextInt(safeFallbacks.length)];

            try {
                JSONObject obj = new JSONObject(fallbackJson);
                String celebrity = obj.getString("correctAnswer");
                String imageUrl = tmdbImageService.getCelebrityImageUrl(celebrity);
                if (imageUrl != null) {
                    obj.put("imageUrl", imageUrl);
                    return obj.toString();
                }
            } catch (Exception e) {
            }

            return fallbackJson;

        } else if ("QUIZ".equalsIgnoreCase(type)) {
            String[] safeQuizFallbacks = {
                    """
                            {
                                "question": "Qual è la capitale dell'Italia?",
                                "options": ["Roma", "Milano", "Napoli", "Firenze"],
                                "correctAnswer": "Roma",
                                "type": "QUIZ"
                            }
                            """,
                    """
                            {
                                "question": "Quanti continenti ci sono sulla Terra?",
                                "options": ["5", "6", "7", "8"],
                                "correctAnswer": "7",
                                "type": "QUIZ"
                            }
                            """,
                    """
                            {
                                "question": "Chi ha dipinto la Gioconda?",
                                "options": ["Leonardo da Vinci", "Michelangelo", "Raffaello", "Caravaggio"],
                                "correctAnswer": "Leonardo da Vinci",
                                "type": "QUIZ"
                            }
                            """,
                    """
                            {
                                "question": "Qual è il pianeta più grande del sistema solare?",
                                "options": ["Giove", "Saturno", "Terra", "Marte"],
                                "correctAnswer": "Giove",
                                "type": "QUIZ"
                            }
                            """,
                    """
                            {
                                "question": "In quale anno è iniziata la Seconda Guerra Mondiale?",
                                "options": ["1939", "1940", "1941", "1938"],
                                "correctAnswer": "1939",
                                "type": "QUIZ"
                            }
                            """
            };

            String fallbackJson = safeQuizFallbacks[new Random().nextInt(safeQuizFallbacks.length)];
            return fallbackJson;

        } else if ("SCREAM_RACE".equalsIgnoreCase(type)) {
            return """
                    {
                        "type": "SCREAM_RACE",
                        "duration": 30,
                        "instructions": "Urla nel microfono per far avanzare la tua squadra!"
                    }
                    """;
        } else if ("TRUE_FALSE".equalsIgnoreCase(type)) {
            String[] safeTrueFalseFallbacks = {
                    """
                            {
                                "question": "Il Sole è una stella.",
                                "options": ["VERO", "FALSO"],
                                "correctAnswer": "VERO",
                                "type": "TRUE_FALSE"
                            }
                            """,
                    """
                            {
                                "question": "La Torre di Pisa si trova a Firenze.",
                                "options": ["VERO", "FALSO"],
                                "correctAnswer": "FALSO",
                                "explanation": "La Torre di Pisa si trova a Pisa, non a Firenze.",
                                "type": "TRUE_FALSE"
                            }
                            """,
                    """
                            {
                                "question": "L'acqua bolle a 100 gradi Celsius a livello del mare.",
                                "options": ["VERO", "FALSO"],
                                "correctAnswer": "VERO",
                                "type": "TRUE_FALSE"
                            }
                            """,
                    """
                            {
                                "question": "La Statua della Libertà si trova a Boston.",
                                "options": ["VERO", "FALSO"],
                                "correctAnswer": "FALSO",
                                "explanation": "La Statua della Libertà si trova a New York, non a Boston.",
                                "type": "TRUE_FALSE"
                            }
                            """,
                    """
                            {
                                "question": "Il Colosseo si trova a Napoli.",
                                "options": ["VERO", "FALSO"],
                                "correctAnswer": "FALSO",
                                "explanation": "Il Colosseo si trova a Roma, non a Napoli.",
                                "type": "TRUE_FALSE"
                            }
                            """
            };

            String fallbackJson = safeTrueFalseFallbacks[new Random().nextInt(safeTrueFalseFallbacks.length)];
            return fallbackJson;

        } else if ("CHRONO".equalsIgnoreCase(type)) {
            return """
                    {
                        "question": "In che anno è caduto il muro di Berlino?",
                        "options": null,
                        "correctAnswer": "1989",
                        "type": "CHRONO"
                    }
                    """;
        } else if ("WHEEL_OF_FORTUNE".equalsIgnoreCase(type)
                || "WHEEL_FORTUNE".equalsIgnoreCase(type)
                || "PROVERB".equalsIgnoreCase(type)) {
            return pickRandomProverb();
        } else if ("ROULETTE".equalsIgnoreCase(type)) {
            return """
                    {
                        "question": "Scegli un colore!",
                        "options": ["ROSSO", "NERO", "VERDE", "BLU", "GIALLO", "BIANCO"],
                        "correctAnswer": "ROSSO",
                        "type": "ROULETTE"
                    }
                    """;
        } else if ("ONE_VS_ONE".equalsIgnoreCase(type) || "1VS1".equalsIgnoreCase(type)) {
            return """
                    {
                        "question": "Qual è l'elemento chimico con simbolo Au?",
                        "options": ["Oro", "Argento", "Alluminio", "Rame"],
                        "correctAnswer": "Oro",
                        "type": "ONE_VS_ONE"
                    }
                    """;
        } else if ("ARENA".equalsIgnoreCase(type)) {
            // Ritorna solo il JSON base perché le domande verranno fetchate dai dispositivi
            return """
                    {
                        "type": "ARENA"
                    }
                    """;
        }

        return "{}";
    }

    private String pickRandomProverb() {
        String[] provs = new String[] {
                "Chi dorme non piglia pesci",
                "L'abito non fa il monaco",
                "Chi va piano va sano e va lontano",
                "Non è tutto oro quel che luccica",
                "Tra il dire e il fare c'è di mezzo il mare",
                "A caval donato non si guarda in bocca",
                "Chi semina vento raccoglie tempesta",
                "Meglio un uovo oggi che una gallina domani",
                "Tanto va la gatta al lardo che ci lascia lo zampino",
                "Meglio soli che male accompagnati",
                "Non si può avere la botte piena e la moglie ubriaca",
                "Tra moglie e marito non mettere il dito",
                "Chi troppo vuole nulla stringe",
                "Il lupo perde il pelo ma non il vizio",
                "Rosso di sera bel tempo si spera",
                "Chi fa da sé fa per tre",
                "Il gioco non vale la candela",
                "Chi trova un amico trova un tesoro",
                "Non tutte le ciambelle riescono col buco",
                "Chi è causa del suo mal pianga sé stesso",
                "Quando il gatto non c'è i topi ballano",
                "Chi ben comincia è a metà dell'opera",
                "L'erba del vicino è sempre più verde",
                "Non rimandare a domani quello che puoi fare oggi",
                "Chi ha tempo non aspetti tempo",
                "Chi va con lo zoppo impara a zoppicare",
                "Chi non risica non rosica",
                "Meglio prevenire che curare",
                "Chi di spada ferisce di spada perisce",
                "Il diavolo fa le pentole ma non i coperchi",
                "Acqua passata non macina più",
                "Chi di speranza vive disperato muore",
                "Non tutto il male viene per nuocere",
                "Chi ha orecchie per intendere intenda",
                "A buon intenditor poche parole",
        };
        return provs[new Random().nextInt(provs.length)];
    }

    private String generateMusicQuestion() {
        MusicTrackDto track = appleMusicCuratorService.getFamousSong();

        JSONObject response = new JSONObject();
        response.put("type", "MUSIC");
        response.put("songTitle", track.title);
        response.put("artist", track.artist);
        response.put("previewUrl", track.previewUrl);
        response.put("albumCover", track.albumCover);
        response.put("year", track.year);
        response.put("source", track.source);
        response.put("payload", JSONObject.NULL);

        return response.toString();
    }
}