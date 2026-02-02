package com.pub_game_be.controller;

import com.pub_game_be.dto.MusicTrackDto;
import com.pub_game_be.service.DeezerCuratorService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/music")
public class MusicController {

    private final DeezerCuratorService service;

    public MusicController(DeezerCuratorService service) {
        this.service = service;
    }

    @GetMapping("/random")
    public MusicTrackDto getRandomSong() {
        return service.getFamousSong();
    }
}
