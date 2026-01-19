package com.pub_game_be.domain.category;

import jakarta.persistence.*; // Use jakarta for Spring Boot 3/4
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "category")
@Data                       // Generates getters, setters, toString, equals, hashCode
@NoArgsConstructor          // Required by JPA
@AllArgsConstructor         // Useful for testing
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private boolean active;
}