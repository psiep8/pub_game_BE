package com.pub_game_be.domain.question;

import com.pub_game_be.domain.category.Category;
import com.pub_game_be.domain.enums.QuestionType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Entity
@Table(name = "question")
@Data                       // Generates getters, setters, toString, equals, hashCode
@NoArgsConstructor          // Required by JPA
@AllArgsConstructor         // Useful for testing
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private Category category;

    @Enumerated(EnumType.STRING)
    private QuestionType type;

    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private List<QuestionOption> options;

    private String text;
    private String correctAnswer;
    private String difficulty; // EASY, MEDIUM, HARD

    @Column(columnDefinition = "TEXT")
    private String payload;
}
