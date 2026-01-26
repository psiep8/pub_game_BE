package com.pub_game_be.controller;
import com.pub_game_be.domain.enums.GameStatus;
import com.pub_game_be.domain.enums.GameType;
import com.pub_game_be.domain.enums.RoundStatus;
import com.pub_game_be.domain.game.Game;
import com.pub_game_be.domain.game_round.GameRound;
import com.pub_game_be.repository.GameRepository;
import com.pub_game_be.repository.GameRoundRepository;
import com.pub_game_be.service.GameService;
import com.pub_game_be.service.QuestionGeneratorService;
import com.pub_game_be.service.SimpleRoundService;
import org.json.JSONObject;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate; // Per WebSocket
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/games")
public class GameController {

    private final GameRepository gameRepo;
    private final GameRoundRepository gameRoundRepository;
    private final SimpleRoundService simpleRoundService;
    private final GameService gameService;
    private final QuestionGeneratorService questionGeneratorService;
    private final SimpMessagingTemplate messagingTemplate;

    // Costruttore completo per Dependency Injection
    public GameController(GameRepository gameRepo,
                          GameRoundRepository gameRoundRepository,
                          SimpleRoundService simpleRoundService,
                          GameService gameService,
                          QuestionGeneratorService questionGeneratorService,
                          SimpMessagingTemplate messagingTemplate) {
        this.gameRepo = gameRepo;
        this.gameRoundRepository = gameRoundRepository;
        this.simpleRoundService = simpleRoundService;
        this.gameService = gameService;
        this.questionGeneratorService = questionGeneratorService;
        this.messagingTemplate = messagingTemplate;
    }

    @PostMapping
    public Game create() {
        Game g = new Game();
        g.setStatus(GameStatus.CREATED);
        return gameRepo.save(g);
    }

    @GetMapping
    public List<Game> getAllGames() {
        return gameRepo.findAll();
    }

    @GetMapping("/{gameId}/generate-ai-round")
    public GameRound generateAiRound(@PathVariable Long gameId,
                                     @RequestParam String category,
                                     @RequestParam String type,
                                     @RequestParam String difficulty) {

        String aiPayload = questionGeneratorService
                .generateQuestionJson(category, type, difficulty);

        Game game = gameRepo.findById(gameId)
                .orElseThrow(() -> new RuntimeException("Gioco non trovato"));

        GameRound round = new GameRound();
        round.setGame(game);

        // TYPE
        round.setType(GameType.valueOf(type.toUpperCase()));

        // STATUS
        round.setStatus(RoundStatus.VOTING);

        // PAYLOAD (JSON STRING) - normalizziamo per WHEEL
        String normalizedPayload = aiPayload;
        if (type != null && (
                "WHEEL_OF_FORTUNE".equalsIgnoreCase(type)
                        || "WHEEL_FORTUNE".equalsIgnoreCase(type)
                        || "PROVERB".equalsIgnoreCase(type)
        )) {
            normalizedPayload = normalizeWheelPayload(aiPayload);
        }

        round.setPayload(normalizedPayload);

        // 🔴 QUESTA È LA RIGA CHIAVE
        int nextIndex = (int) gameRoundRepository.countByGameId(gameId);
        round.setRoundIndex(nextIndex);

        GameRound savedRound = gameRoundRepository.save(round);

        messagingTemplate.convertAndSend(
                "/topic/game/" + gameId,
                savedRound
        );

        return savedRound;
    }

    private String normalizeWheelPayload(String aiPayload) {
        if (aiPayload == null) return new JSONObject().put("proverb", "").toString();
        String p = aiPayload.trim();

        // Try extracting the raw proverb text; this function returns null if nothing found
        String raw = tryExtractRawProverb(p);

        if (raw == null) {
            // As a last resort, if p looks like JSON try to extract 'question' or nested text
            if (p.startsWith("{")) {
                try {
                    JSONObject obj = new JSONObject(p);
                    if (obj.has("question")) {
                        String q = obj.getString("question");
                        raw = extractProverbFromQuestionText(q);
                    }
                    if (raw == null && obj.has("payload")) {
                        Object nested = obj.get("payload");
                        String nestedStr = String.valueOf(nested);
                        raw = tryExtractRawProverb(nestedStr);
                    }
                } catch (Exception ignored) {
                }
            }
        }

        // If still null, fallback to using the trimmed input as the proverb text
        if (raw == null) raw = p;

        // Wrap into a valid JSON object so MySQL's JSON column accepts it
        JSONObject out = new JSONObject();
        out.put("proverb", raw);
        return out.toString();
    }

    /**
     * Try to extract a raw proverb string from various payload shapes.
     * Returns the raw proverb or null if not found.
     */
    private String tryExtractRawProverb(String p) {
        if (p == null) return null;
        String trimmed = p.trim();

        // 1) If it's a JSON object string, parse and look for common fields
        if (trimmed.startsWith("{")) {
            try {
                JSONObject obj = new JSONObject(trimmed);
                if (obj.has("proverb")) return obj.getString("proverb");
                if (obj.has("payload")) {
                    Object nested = obj.get("payload");
                    String nestedStr = String.valueOf(nested).trim();
                    // If nested is JSON string, recurse
                    if (nestedStr.startsWith("{")) return tryExtractRawProverb(nestedStr);
                    // else if nested likely plain string
                    return nestedStr;
                }
                if (obj.has("question")) {
                    String q = obj.getString("question");
                    String ext = extractProverbFromQuestionText(q);
                    if (ext != null) return ext;
                }
            } catch (Exception ignored) {
            }
        }

        // 2) If it's an escaped JSON in quotes like "{...}", try unescape
        if ((trimmed.startsWith("\"{") && trimmed.endsWith("}\"")) || trimmed.contains("\\{")) {
            String unquoted = trimmed;
            if (unquoted.startsWith("\"") && unquoted.endsWith("\"")) {
                unquoted = unquoted.substring(1, unquoted.length() - 1);
            }
            // Unescape common sequences (use replace to avoid regex pitfalls)
            unquoted = unquoted.replace("\\\"", "\"");
            unquoted = unquoted.replace("\\\\", "\\");
            return tryExtractRawProverb(unquoted);
        }

        // 3) Try to extract quoted substring from plain question text
        String ext = extractProverbFromQuestionText(trimmed);
        if (ext != null) return ext;

        // 4) Not found
        return null;
    }

    private String extractProverbFromQuestionText(String text) {
        if (text == null) return null;
        String t = text.trim();

        // look for single quotes '...'
        int first = t.indexOf('\'');
        if (first >= 0) {
            int second = t.indexOf('\'', first + 1);
            if (second > first) {
                String inside = t.substring(first + 1, second).trim();
                if (inside.length() > 3) return inside;
            }
        }

        // look for double quotes "..."
        first = t.indexOf('"');
        if (first >= 0) {
            int second = t.indexOf('"', first + 1);
            if (second > first) {
                String inside = t.substring(first + 1, second).trim();
                if (inside.length() > 3) return inside;
            }
        }

        // try pattern: Il proverbio ... significa
        String marker = "Il proverbio ";
        int idx = t.indexOf(marker);
        if (idx >= 0) {
            int start = idx + marker.length();
            int end = t.indexOf(" significa", start);
            if (end > start) {
                String inside = t.substring(start, end).trim();
                // strip quotes if present
                if ((inside.startsWith("\"") && inside.endsWith("\"")) || (inside.startsWith("'") && inside.endsWith("'"))) {
                    inside = inside.substring(1, inside.length() - 1);
                }
                if (inside.length() > 3) return inside;
            }
        }

        return null;
    }

    @PostMapping("/{id}/round")
    public GameRound nextRound(
            @PathVariable Long id,
            @RequestParam(defaultValue = "EASY") String difficulty) {
        Game game = gameRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Gioco non trovato"));

        GameRound round = simpleRoundService.createRound(game, difficulty);

        // Anche qui inviamo via WebSocket per aggiornare i client
        messagingTemplate.convertAndSend("/topic/game/" + id, round);

        return round;
    }

    @GetMapping("/{gameId}/current-round")
    public ResponseEntity<GameRound> getCurrentRound(@PathVariable Long gameId) {
        return gameService.getCurrentRound(gameId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}