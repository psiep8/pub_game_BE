package com.pub_game_be.service;

import com.pub_game_be.dto.AppleMusicTrack;
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
        // ========== WHEEL_FORTUNE / WHEEL_OF_FORTUNE: Restituisce SOLO il proverbio nel payload ==========
        if ("WHEEL_OF_FORTUNE".equalsIgnoreCase(type)
                || "WHEEL_FORTUNE".equalsIgnoreCase(type)
                || "PROVERB".equalsIgnoreCase(type)) {
            String proverb = pickRandomProverb();

            System.out.println("🎰 WHEEL_FORTUNE generato");
            System.out.println("📜 Proverbio: " + proverb);

            // Restituiamo SOLO la stringa del proverbio (non un JSON annidato)
            return proverb;
        }

        String difficultyContext = switch (difficulty.toLowerCase()) {
            case "facile" -> "Usa personaggi/domande molto popolari, quasi ovvi.";
            case "medio" -> "Usa personaggi/domande di buona fama, ma non iconici.";
            case "difficile" -> "Sii specifico. Usa personaggi/dettagli meno noti.";
            default -> "Difficoltà bilanciata.";
        };

        String prompt;

        // ========== IMAGE_BLUR: Usa TMDB per immagini verificate ==========
        if ("IMAGE_BLUR".equalsIgnoreCase(type)) {
            String recentList = recentCelebrities.isEmpty()
                    ? "nessuno"
                    : String.join(", ", recentCelebrities);

            prompt = String.format(
                    "Sei il presentatore di un quiz televisivo basato sul riconoscimento visivo.\n" +
                            "CATEGORIA: %s\n" +
                            "LIVELLO: %s (%s)\n\n" +
                            "REGOLE DI SELEZIONE:\n" +
                            "1. Scegli UNA celebrità DIVERSA ogni volta\n" +
                            "2. NON USARE QUESTI (già usciti): %s\n" +
                            "3. PREFERENZA per personaggi ITALIANI (60%% italiani, 40%% internazionali)\n" +
                            "4. Varia tra: attori, cantanti, sportivi, registi, conduttori TV\n" +
                            "5. USA SOLO personaggi presenti su TMDB/IMDb (cinema, TV, musica popolare)\n\n" +
                            "ESEMPI ITALIANI livello MEDIO:\n" +
                            "- Cinema: Alessandro Borghi, Luca Marinelli, Jasmine Trinca, Valeria Golino, Elio Germano\n" +
                            "- Musica: Mahmood, Blanco, Elodie, Annalisa, Giorgia, Tiziano Ferro\n" +
                            "- Sport famosi: Francesco Totti, Valentino Rossi (se hanno apparizioni TV/film)\n" +
                            "- TV: Alessandro Cattelan, Fabio Fazio, Geppi Cucciari\n\n" +
                            "ESEMPI INTERNAZIONALI livello MEDIO:\n" +
                            "- Jake Gyllenhaal, Margot Robbie, Zendaya, Pedro Pascal, Oscar Isaac, Ryan Gosling, Emma Stone\n\n" +
                            "Rispondi SOLO con JSON valido (NO markdown, NO testo extra):\n" +
                            "{\n" +
                            "  \"question\": \"Chi è questa persona?\",\n" +
                            "  \"correctAnswer\": \"Nome Completo Esatto\",\n" +
                            "  \"type\": \"IMAGE_BLUR\",\n" +
                            "  \"options\": null\n" +
                            "}\n\n" +
                            "IMPORTANTE:\n" +
                            "- Usa il nome COMPLETO esatto (es: 'Leonardo DiCaprio', non 'Leo DiCaprio')\n" +
                            "- Verifica che sia una persona reale con presenza su TMDB\n" +
                            "- Non inventare nomi o usare persone non famose",
                    category, difficulty, difficultyContext, recentList
            );
        }
        if ("MUSIC".equalsIgnoreCase(type)) {
            return generateMusicQuestion();
        }
        if ("QUIZ".equalsIgnoreCase(type)) {
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
                            "ESEMPIO NON VALIDO (correctAnswer non è nelle options):\n" +
                            "{\n" +
                            "  \"question\": \"Qual è la capitale della Francia?\",\n" +
                            "  \"options\": [\"Londra\", \"Berlino\", \"Madrid\", \"Roma\"],\n" +
                            "  \"correctAnswer\": \"Parigi\"\n" +
                            "}\n\n" +
                            "Rispondi SOLO con JSON valido (NO markdown, NO testo extra).\n" +
                            "IMPORTANTE: Verifica che 'correctAnswer' sia ESATTAMENTE uguale a una delle options!",
                    category, difficulty, difficultyContext
            );
        }
// ========== TRUE_FALSE: Prompt migliorato ==========
        else if ("TRUE_FALSE".equalsIgnoreCase(type)) {
            prompt = String.format(
                    "Sei il presentatore di un quiz televisivo.\n" +
                            "CATEGORIA: %s\n" +
                            "LIVELLO: %s (%s)\n\n" +
                            "Genera UNA domanda VERO/FALSO.\n\n" +
                            "REGOLE RIGIDE:\n" +
                            "1. La domanda deve avere risposta oggettiva (non opinioni)\n" +
                            "2. La risposta deve essere verificabile\n" +
                            "3. 'options' deve essere ESATTAMENTE [\"VERO\", \"FALSO\"]\n" +
                            "4. 'correctAnswer' deve essere \"VERO\" o \"FALSO\"\n" +
                            "5. NON inventare fatti inesistenti\n\n" +
                            "ESEMPIO VALIDO:\n" +
                            "{\n" +
                            "  \"question\": \"Il Sole è una stella.\",\n" +
                            "  \"options\": [\"VERO\", \"FALSO\"],\n" +
                            "  \"correctAnswer\": \"VERO\",\n" +
                            "  \"type\": \"TRUE_FALSE\"\n" +
                            "}\n\n" +
                            "Rispondi SOLO con JSON valido (NO markdown).",
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
        } else {
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
                if ("QUIZ".equalsIgnoreCase(type) || "TRUE_FALSE".equalsIgnoreCase(type)) {
                    if (!validateQuizJson(jsonObj)) {
                        System.err.println("⚠️ Domanda non valida, uso fallback");
                        return getFallbackJson(type);
                    }
                }
                if ("IMAGE_BLUR".equalsIgnoreCase(type) && jsonObj.has("correctAnswer")) {
                    String celebrity = jsonObj.getString("correctAnswer");

                    // Ottieni immagine da TMDB
                    String imageUrl = tmdbImageService.getCelebrityImageUrl(celebrity);

                    if (imageUrl != null && !imageUrl.isEmpty()) {
                        jsonObj.put("imageUrl", imageUrl);
                        addToRecentCelebrities(celebrity);

                        System.out.println("✅ Celebrità: " + celebrity);
                        System.out.println("🖼️ TMDB Image URL: " + imageUrl);
                        System.out.println("📋 Cache recenti: " + recentCelebrities);
                    } else {
                        // Se TMDB non trova l'immagine, riprova con un'altra celebrità
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
     * Aggiunge una celebrità alla cache evitando ripetizioni
     */
    private void addToRecentCelebrities(String celebrity) {
        recentCelebrities.add(celebrity);

        // Mantieni solo gli ultimi MAX_RECENT
        if (recentCelebrities.size() > MAX_RECENT) {
            Iterator<String> iterator = recentCelebrities.iterator();
            if (iterator.hasNext()) {
                iterator.next();
                iterator.remove();
            }
        }
    }

    private boolean validateQuizJson(JSONObject jsonObj) {
        String correctAnswer = jsonObj.getString("correctAnswer");
        JSONArray options = jsonObj.getJSONArray("options");

        for (int i = 0; i < options.length(); i++) {
            if (options.getString(i).equals(correctAnswer)) {
                return true; // ✅ Valido
            }
        }

        System.err.println("❌ VALIDAZIONE FALLITA!");
        System.err.println("Risposta: " + correctAnswer);
        System.err.println("Opzioni: " + options);
        return false; // ❌ Non valido
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
            // Fallback con celebrità ultra-famose che TMDB ha sicuramente
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

            // Anche per fallback, cerca immagine su TMDB
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
            // 🔥 FALLBACK SICURI con 10+ domande verificate
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
                """,
                    """
                {
                    "question": "Chi ha scritto 'I Promessi Sposi'?",
                    "options": ["Alessandro Manzoni", "Dante Alighieri", "Giovanni Verga", "Italo Calvino"],
                    "correctAnswer": "Alessandro Manzoni",
                    "type": "QUIZ"
                }
                """,
                    """
                {
                    "question": "Qual è l'oceano più grande?",
                    "options": ["Pacifico", "Atlantico", "Indiano", "Artico"],
                    "correctAnswer": "Pacifico",
                    "type": "QUIZ"
                }
                """,
                    """
                {
                    "question": "Quanti lati ha un esagono?",
                    "options": ["6", "5", "7", "8"],
                    "correctAnswer": "6",
                    "type": "QUIZ"
                }
                """,
                    """
                {
                    "question": "Chi ha inventato il telefono?",
                    "options": ["Alexander Graham Bell", "Thomas Edison", "Nikola Tesla", "Guglielmo Marconi"],
                    "correctAnswer": "Alexander Graham Bell",
                    "type": "QUIZ"
                }
                """,
                    """
                {
                    "question": "Qual è la montagna più alta del mondo?",
                    "options": ["Monte Everest", "K2", "Monte Bianco", "Kilimanjaro"],
                    "correctAnswer": "Monte Everest",
                    "type": "QUIZ"
                }
                """
            };

            String fallbackJson = safeQuizFallbacks[new Random().nextInt(safeQuizFallbacks.length)];
            System.out.println("🔄 Uso fallback QUIZ sicuro");
            return fallbackJson;
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
        } else if ("WHEEL_OF_FORTUNE".equalsIgnoreCase(type)
                || "WHEEL_FORTUNE".equalsIgnoreCase(type)
                || "PROVERB".equalsIgnoreCase(type)) {
            // Fallback: restituisci solo il proverbio come stringa
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
        }

        return "{}";
    }

    /**
     * Lista curata di proverbi italiani famosi per la Ruota della Fortuna
     */
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

        // 🔥 JSON PIATTO (no doppio nesting)
        JSONObject response = new JSONObject();
        response.put("type", "MUSIC");
        response.put("songTitle", track.title);
        response.put("artist", track.artist);
        response.put("previewUrl", track.previewUrl);
        response.put("albumCover", track.albumCover);
        response.put("year", track.year);
        response.put("source", track.source);
        response.put("payload", JSONObject.NULL); // Non serve

        return response.toString();
    }

}

