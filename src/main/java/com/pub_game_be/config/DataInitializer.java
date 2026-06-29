package com.pub_game_be.config;

import com.pub_game_be.domain.enums.GameStatus;
import com.pub_game_be.domain.game.Game;
import com.pub_game_be.repository.GameRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private final GameRepository gameRepository;

    public DataInitializer(GameRepository gameRepository) {
        this.gameRepository = gameRepository;
    }

    @Override
    public void run(String... args) {
        if (gameRepository.count() == 0) {
            Game game = new Game();
            game.setStatus(GameStatus.CREATED);
            gameRepository.save(game);
            System.out.println("✅ Partita iniziale creata con ID: " + game.getId());
        }
    }
}
