package com.pub_game_be.controller;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;
import java.util.Map;

@Controller
public class GameWebSocketController {

    // Quando un giocatore risponde dal telefono, invia a /app/game/{gameId}/answer
    @MessageMapping("/game/{gameId}/answer")
    @SendTo("/topic/game/{gameId}/responses")
    public Map<String, Object> handleAnswer(@DestinationVariable Long gameId, Map<String, Object> payload) {
        // Il payload conterrà { "playerName": "Mario", "answerIndex": 2 }
        // Per ora facciamo "rimbalzare" il messaggio alla TV
        return payload;
    }

    // Quando la TV cambia stato (es. inizia il round), avvisa i telefoni
    @MessageMapping("/game/{gameId}/status")
    @SendTo("/topic/game/{gameId}/status")
    public Map<String, String> updateStatus(@DestinationVariable Long gameId, Map<String, String> status) {
        return status;
    }
}