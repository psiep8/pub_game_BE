package com.pub_game_be.controller;

import com.pub_game_be.domain.category.Category;
import com.pub_game_be.domain.enums.GameStatus;
import com.pub_game_be.domain.game.Game;
import com.pub_game_be.domain.game_round.GameRound;
import com.pub_game_be.repository.CategoryRepository;
import com.pub_game_be.repository.GameRepository;
import com.pub_game_be.service.SimpleRoundService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin(origins = "http://localhost:4200")
@RestController
@RequestMapping("/categories")
public class CategoryController {

    private final CategoryRepository categoryRepo;

    public CategoryController(CategoryRepository categoryRepo) {
        this.categoryRepo = categoryRepo;
    }

    @GetMapping
    public List<Category> getAll() {
        // Restituisce tutte le categorie (STORIA, SPORT, CINEMA, ecc.)
        return categoryRepo.findAll();
    }
}