package com.pub_game_be.domain.game;

import com.pub_game_be.domain.enums.GameStatus;
import jakarta.persistence.*; // This covers @Entity, @Table, @Id, @GeneratedValue, etc.
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "game")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Game {

    @Id // Now correctly using jakarta.persistence.Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private GameStatus status;
}