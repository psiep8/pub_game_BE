package com.pub_game_be.controller;

import com.pub_game_be.domain.enums.GameStatus;
import com.pub_game_be.domain.enums.GameType;
import com.pub_game_be.domain.enums.RoundStatus;
import com.pub_game_be.domain.game.Game;
import com.pub_game_be.domain.game_round.GameRound;
import com.pub_game_be.repository.GameRepository;
import com.pub_game_be.repository.GameRoundRepository; // Assicurati di averlo
import com.pub_game_be.service.GameService;
import com.pub_game_be.service.QuestionGeneratorService;
import com.pub_game_be.service.SimpleRoundService;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate; // Per WebSocket
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin(origins = "http://localhost:4200")
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

        // PAYLOAD (JSON STRING)
        round.setPayload(aiPayload);

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