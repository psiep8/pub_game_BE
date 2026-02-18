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

    private final TMDBImageService tmdbImageService;
    private final AppleMusicCuratorService appleMusicCuratorService;

    // Cache per evitare ripetizioni
    private final Set<String> recentCelebrities = ConcurrentHashMap.newKeySet();
    private final int MAX_RECENT = 20;

    public QuestionGeneratorService(TMDBImageService tmdbImageService, AppleMusicCuratorService appleMusicCuratorService) {
        this.tmdbImageService = tmdbImageService;
        this.appleMusicCuratorService = appleMusicCuratorService;
    }

    public String generateQuestionJson(String category, String type, String difficulty) {
        RestTemplate restTemplate = new RestTemplate();

        // ========== ROULETTE ==========
        if ("ROULETTE".equalsIgnoreCase(type)) {
            String[] colors = {"ROSSO", "NERO", "VERDE", "BLU", "GIALLO", "BIANCO"};
            String winningColor = colors[new Random().nextInt(colors.length)];

            JSONObject response = new JSONObject();
            response.put("type", "ROULETTE");
            response.put("question", "Scegli un colore!");
            response.put("correctAnswer", winningColor);
            response.put("options", new JSONArray(colors));
            response.put("payload", JSONObject.NULL);

            System.out.println("🎰 ROULETTE generata - Colore vincente: " + winningColor);
            return response.toString();
        }

        // ========== WHEEL_OF_FORTUNE ==========
        if ("WHEEL_OF_FORTUNE".equalsIgnoreCase(type)
                || "WHEEL_FORTUNE".equalsIgnoreCase(type)
                || "PROVERB".equalsIgnoreCase(type)) {
            String proverb = pickRandomProverb();
            System.out.println("🎰 WHEEL_FORTUNE generato");
            System.out.println("📜 Proverbio: " + proverb);
            return proverb;
        }

        // ========== MUSIC ==========
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

        // ========== IMAGE_BLUR ==========
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
                    category, difficulty, difficultyContext, recentList
            );
        }
        // ========== TRUE_FALSE con explanation ==========
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
                    shouldBeTrue ? "VERO" : "FALSO",  // Forza la risposta
                    shouldBeTrue ? "VERO" : "FALSO"
            );
        }
        // ========== QUIZ con validazione ==========
        else if ("QUIZ".equalsIgnoreCase(type)) {
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
                    category, difficulty, difficultyContext
            );
        }
        // ========== CHRONO ==========
        else if ("CHRONO".equalsIgnoreCase(type)) {
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
                    category, difficulty, difficultyContext
            );
        }
        if ("ONE_VS_ONE".equalsIgnoreCase(type) || "1VS1".equalsIgnoreCase(type)) {
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
                    category, difficulty, difficultyContext
            );
        }
        // ========== Altro ==========
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

            // 🔥 VALIDA JSON
            try {
                JSONObject jsonObj = new JSONObject(cleanedJson);

                // 🔥 VALIDAZIONE per QUIZ e TRUE_FALSE
                if ("QUIZ".equalsIgnoreCase(type) || "TRUE_FALSE".equalsIgnoreCase(type)) {
                    if (!validateQuizJson(jsonObj, type)) {
                        System.err.println("⚠️ Domanda non valida, uso fallback");
                        return getFallbackJson(type);
                    }
                }

                // Se IMAGE_BLUR, cerca immagine su TMDB
                if ("IMAGE_BLUR".equalsIgnoreCase(type) && jsonObj.has("correctAnswer")) {
                    String celebrity = jsonObj.getString("correctAnswer");

                    String imageUrl = tmdbImageService.getCelebrityImageUrl(celebrity);

                    if (imageUrl != null && !imageUrl.isEmpty()) {
                        jsonObj.put("imageUrl", imageUrl);
                        addToRecentCelebrities(celebrity);

                        System.out.println("✅ Celebrità: " + celebrity);
                        System.out.println("🖼️ TMDB Image URL: " + imageUrl);
                        System.out.println("📋 Cache recenti: " + recentCelebrities);
                    } else {
                        System.out.println("⚠️ TMDB non ha immagine per: " + celebrity);
                        System.out.println("🔄 Rigenerando domanda...");
                        return generateQuestionJson(category, type, difficulty);
                    }

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
     * 🔍 Valida che la risposta corretta sia nelle opzioni
     * Funziona per QUIZ e TRUE_FALSE
     */
    private boolean validateQuizJson(JSONObject jsonObj, String type) {
        try {
            if (!jsonObj.has("correctAnswer") || !jsonObj.has("options")) {
                System.err.println("❌ Manca correctAnswer o options");
                return false;
            }

            String correctAnswer = jsonObj.getString("correctAnswer");
            JSONArray options = jsonObj.getJSONArray("options");

            // 🔥 TRUE_FALSE: validazione specifica
            if ("TRUE_FALSE".equalsIgnoreCase(type)) {
                if (options.length() != 2) {
                    System.err.println("❌ TRUE_FALSE deve avere 2 opzioni");
                    return false;
                }

                boolean hasVero = false;
                boolean hasFalso = false;
                for (int i = 0; i < options.length(); i++) {
                    String opt = options.getString(i);
                    if ("VERO".equals(opt)) hasVero = true;
                    if ("FALSO".equals(opt)) hasFalso = true;
                }

                if (!hasVero || !hasFalso) {
                    System.err.println("❌ TRUE_FALSE deve avere opzioni [VERO, FALSO]");
                    return false;
                }

                if (!"VERO".equals(correctAnswer) && !"FALSO".equals(correctAnswer)) {
                    System.err.println("❌ TRUE_FALSE correctAnswer deve essere VERO o FALSO");
                    return false;
                }

                // 🔥 Se correctAnswer è FALSO, verifica explanation
                if ("FALSO".equals(correctAnswer)) {
                    if (!jsonObj.has("explanation") || jsonObj.getString("explanation").isEmpty()) {
                        System.err.println("⚠️ TRUE_FALSE con FALSO dovrebbe avere 'explanation'");
                    }
                }

                return true;
            }

            // 🔥 QUIZ: validazione standard
            boolean found = false;
            for (int i = 0; i < options.length(); i++) {
                if (options.getString(i).equals(correctAnswer)) {
                    found = true;
                    break;
                }
            }

            if (!found) {
                System.err.println("❌ VALIDAZIONE FALLITA!");
                System.err.println("Risposta corretta: " + correctAnswer);
                System.err.println("Opzioni: " + options);
                return false;
            }

            return true;

        } catch (Exception e) {
            System.err.println("❌ Errore validazione: " + e.getMessage());
            return false;
        }
    }

    /**
     * Aggiunge celebrità alla cache
     */
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
                System.err.println("❌ Errore fallback TMDB: " + e.getMessage());
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
            System.out.println("🔄 Uso fallback QUIZ sicuro");
            return fallbackJson;

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
            System.out.println("🔄 Uso fallback TRUE_FALSE sicuro");
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
        }

        return "{}";
    }

    private String pickRandomProverb() {
        String[] provs = new String[]{
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