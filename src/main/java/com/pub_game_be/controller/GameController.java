package com.pub_game_be.controller;

import com.pub_game_be.domain.category.Category;
import com.pub_game_be.domain.enums.GameStatus;
import com.pub_game_be.domain.game.Game;
import com.pub_game_be.domain.game_round.GameRound;
import com.pub_game_be.domain.question.Question;
import com.pub_game_be.repository.GameRepository;
import com.pub_game_be.repository.QuestionRepository;
import com.pub_game_be.service.SimpleRoundService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin(origins = "http://localhost:4200")
@RestController
@RequestMapping("/games")
public class GameController {

    private final GameRepository gameRepo;
    private final SimpleRoundService simpleRoundService;
    private final QuestionRepository questionRepository;

    public GameController(GameRepository gameRepo, SimpleRoundService simpleRoundService, QuestionRepository questionRepository) {
        this.gameRepo = gameRepo;
        this.simpleRoundService = simpleRoundService;
        this.questionRepository = questionRepository;
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

    @GetMapping("/test-create")
    public Game testCreate() {
        return create(); // Chiama il metodo POST internamente
    }

    @PostMapping("/{id}/round")
    public GameRound nextRound(
            @PathVariable Long id,
            @RequestParam(defaultValue = "EASY") String difficulty) {
        Game game = gameRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Gioco non trovato"));
        return simpleRoundService.createRound(game, difficulty);
    }

}
