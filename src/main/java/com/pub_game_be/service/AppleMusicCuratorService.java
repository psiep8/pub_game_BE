package com.pub_game_be.service;

import com.pub_game_be.dto.AppleMusicResponse;
import com.pub_game_be.dto.AppleMusicTrack;
import com.pub_game_be.dto.MusicTrackDto;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.ObjectMapper;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Random;

@Service
public class AppleMusicCuratorService {

    private static final String API_URL = "https://itunes.apple.com/search";
    private final RestTemplate restTemplate;
    private final Random random = new Random();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 🇮🇹 Artisti italiani famosi
    private static final List<String> ITALIAN_ARTISTS = List.of(
            "Vasco Rossi",
            "Ligabue",
            "Ultimo",
            "Eros Ramazzotti",
            "Laura Pausini",
            "Zucchero"
    );

    // 🌍 Artisti internazionali
    private static final List<String> INTERNATIONAL_ARTISTS = List.of(
            "The Weeknd",
            "Queen",
            "Coldplay",
            "Michael Jackson",
            "Daft Punk",
            "Adele"
    );

    public AppleMusicCuratorService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public MusicTrackDto getFamousSong() {
        boolean italian = random.nextDouble() < 0.7;
        return italian ? getItalianSong() : getInternationalSong();
    }

    private MusicTrackDto getItalianSong() {
        return searchByArtist(
                ITALIAN_ARTISTS.get(random.nextInt(ITALIAN_ARTISTS.size())),
                "italian"
        );
    }

    private MusicTrackDto getInternationalSong() {
        return searchByArtist(
                INTERNATIONAL_ARTISTS.get(random.nextInt(INTERNATIONAL_ARTISTS.size())),
                "international"
        );
    }

    private MusicTrackDto searchByArtist(String artist, String type) {
        try {
            String query = URLEncoder.encode(artist, StandardCharsets.UTF_8);
            String url = API_URL + "?term=" + query + "&entity=song&limit=20";

            String rawJson = restTemplate.getForObject(url, String.class);

            AppleMusicResponse response =
                    objectMapper.readValue(rawJson, AppleMusicResponse.class);

            if (response == null || response.results == null || response.results.isEmpty()) {
                throw new RuntimeException("Empty Apple Music response");
            }

            AppleMusicTrack track =
                    response.results.get(random.nextInt(response.results.size()));

            MusicTrackDto dto = new MusicTrackDto();
            dto.id = String.valueOf(track.trackId);
            dto.title = track.trackName;
            dto.artist = track.artistName;
            dto.previewUrl = track.previewUrl;
            dto.albumCover = track.artworkUrl100.replace("100x100", "600x600");
            dto.year = track.releaseDate != null
                    ? Integer.parseInt(track.releaseDate.substring(0, 4))
                    : null;

            dto.type = type;
            dto.source = "apple-music";

            System.out.println("🎵 Apple Music: " + dto.title + " - " + dto.artist);
            return dto;

        } catch (Exception e) {
            System.err.println("❌ Apple Music error: " + e.getMessage());
            return getFallbackSong(type);
        }
    }

    private MusicTrackDto getFallbackSong(String type) {
        MusicTrackDto dto = new MusicTrackDto();

        dto.id = "fallback_apple_1";
        dto.title = "Blinding Lights";
        dto.artist = "The Weeknd";
        dto.previewUrl =
                "https://audio-ssl.itunes.apple.com/itunes-assets/AudioPreview125/v4/5b/2a/7a/5b2a7a3e-3cfa-7f08-f6b4-dc43d4a2c6b5/mzaf_11897835066034327923.plus.aac.p.m4a";
        dto.albumCover = "https://is1-ssl.mzstatic.com/image/thumb/Music124/v4/6b/84/34/6b8434e6-bb1f-8c6c-2c5b-6c39d25f1a38/20UMGIM17448.rgb.jpg/600x600bb.jpg";
        dto.year = 2020;

        dto.type = type;
        dto.source = "fallback";

        return dto;
    }

}
