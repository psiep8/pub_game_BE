package com.pub_game_be.service;

import com.pub_game_be.domain.category.Category;
import com.pub_game_be.domain.enums.GameType;
import com.pub_game_be.domain.enums.RoundStatus;
import com.pub_game_be.domain.game.Game;
import com.pub_game_be.domain.game_round.GameRound;
import com.pub_game_be.domain.question.Question;
import com.pub_game_be.repository.CategoryRepository;
import com.pub_game_be.repository.GameRoundRepository;
import com.pub_game_be.repository.QuestionRepository;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Random;

@Service
public class SimpleRoundService {

    private final QuestionRepository questionRepository;
    private final CategoryRepository categoryRepository;
    private final GameRoundRepository gameRoundRepository;

    public SimpleRoundService(QuestionRepository questionRepository,
                              CategoryRepository categoryRepository,
                              GameRoundRepository gameRoundRepository) {
        this.questionRepository = questionRepository;
        this.categoryRepository = categoryRepository;
        this.gameRoundRepository = gameRoundRepository;
    }

    public GameRound createRound(Game game, String difficulty) {
        // 1. Recupera tutte le categorie disponibili
        List<Category> allCategories = categoryRepository.findAll();
        if (allCategories.isEmpty()) {
            throw new RuntimeException("Nessuna categoria trovata nel database!");
        }

        // 2. Sceglie una categoria a caso
        Category randomCat = allCategories.get(new Random().nextInt(allCategories.size()));

        // 3. Cerca una domanda casuale per quella categoria e quella difficoltà
        // Nota: Assicurati che nel QuestionRepository esista il metodo findRandomByCategoryAndDifficulty
        Question question = questionRepository.findRandomByCategoryAndDifficulty(randomCat.getId(), difficulty)
                .orElseThrow(() -> new RuntimeException("Nessuna domanda trovata per la categoria "
                        + randomCat.getName() + " con difficoltà " + difficulty));

        GameRound round = getGameRound(game, question);
        // ----------------------------

        return gameRoundRepository.save(round);
    }

    private static @NonNull GameRound getGameRound(Game game, Question question) {
        GameRound round = new GameRound();
        round.setGame(game);
        round.setQuestion(question);
        round.setPayload(question.getPayload());

        // --- AGGIUNGI QUESTE RIGHE ---
        round.setStatus(RoundStatus.CREATED); // O lo stato iniziale che hai definito in RoundStatus
        round.setType(GameType.QUIZ);         // O prendilo dal type della domanda se preferisci
        round.setRoundIndex(1);               // Dovresti calcolarlo in base ai round esistenti, ma per ora mettiamo 1
        return round;
    }
}