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

    // 🇮🇹 ARTISTI ITALIANI ICONICI (70%)
    private static final List<String> ITALIAN_ARTISTS = List.of(
            // Classici leggendari
            "Vasco Rossi", "Ligabue", "Zucchero", "Lucio Battisti",
            "Eros Ramazzotti", "Laura Pausini", "Tiziano Ferro",
            "Gianna Nannini", "Renato Zero", "Claudio Baglioni",

            // Pop contemporaneo
            "Ultimo", "Mahmood", "Elodie", "Annalisa",
            "Nek", "Emma Marrone", "Alessandra Amoroso",

            // Dance/Rap italiano
            "Gabry Ponte", "Gigi D'Agostino", "Eiffel 65",
            "Fedez", "J-Ax", "Marracash", "Ghali",

            // Sanremo & Icons
            "Giorgia", "Elisa", "Marco Mengoni", "Alessandra Amoroso",
            "Max Pezzali", "883", "Rocco Hunt", "Boomdabash"
    );

    // 🌍 ARTISTI INTERNAZIONALI ICONICI (30%)
    private static final List<String> INTERNATIONAL_ARTISTS = List.of(
            // Pop/Rock Legends
            "Queen", "The Beatles", "Michael Jackson", "Madonna",
            "Coldplay", "U2", "Bon Jovi", "The Rolling Stones",

            // Pop moderno
            "The Weeknd", "Ed Sheeran", "Adele", "Bruno Mars",
            "Taylor Swift", "Ariana Grande", "Justin Bieber",

            // Dance/Electronic
            "Daft Punk", "David Guetta", "Calvin Harris", "Avicii",
            "Martin Garrix", "The Chainsmokers",

            // Hip Hop/R&B
            "Drake", "Eminem", "Rihanna", "Beyoncé", "Kanye West"
    );

    public AppleMusicCuratorService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public MusicTrackDto getFamousSong() {
        boolean italian = random.nextDouble() < 0.7; // 70% italiane
        System.out.println("🎵 Selezione: " + (italian ? "🇮🇹 ITALIANA" : "🌍 INTERNAZIONALE"));
        return italian ? getItalianSong() : getInternationalSong();
    }

    private MusicTrackDto getItalianSong() {
        return searchTopSongByArtist(
                ITALIAN_ARTISTS.get(random.nextInt(ITALIAN_ARTISTS.size())),
                "italian"
        );
    }

    private MusicTrackDto getInternationalSong() {
        return searchTopSongByArtist(
                INTERNATIONAL_ARTISTS.get(random.nextInt(INTERNATIONAL_ARTISTS.size())),
                "international"
        );
    }

    /**
     * 🔥 Cerca TOP SONG (più famosa) dell'artista
     */
    private MusicTrackDto searchTopSongByArtist(String artist, String type) {
        try {
            String query = URLEncoder.encode(artist, StandardCharsets.UTF_8);

            // 🎯 Ordina per POPULARITY (le canzoni più famose vengono prima)
            String url = API_URL + "?term=" + query +
                    "&entity=song&limit=50&sort=popularity";

            System.out.println("🔍 Searching: " + artist);

            String rawJson = restTemplate.getForObject(url, String.class);
            AppleMusicResponse response = objectMapper.readValue(rawJson, AppleMusicResponse.class);

            if (response == null || response.results == null || response.results.isEmpty()) {
                System.out.println("⚠️ No results for " + artist + ", using fallback");
                return getFallbackSong(type);
            }

            // 🔥 Filtra solo canzoni CON PREVIEW
            List<AppleMusicTrack> tracksWithPreview = response.results.stream()
                    .filter(t -> t.previewUrl != null && !t.previewUrl.isEmpty())
                    .toList();

            if (tracksWithPreview.isEmpty()) {
                System.out.println("⚠️ No preview for " + artist + ", using fallback");
                return getFallbackSong(type);
            }

            // 🎯 PRENDI LA PRIMA (più popolare) con preview
            AppleMusicTrack track = tracksWithPreview.get(0);

            MusicTrackDto dto = new MusicTrackDto();
            dto.id = String.valueOf(track.trackId);
            dto.title = track.trackName;
            dto.artist = track.artistName;
            dto.previewUrl = track.previewUrl;

            // Cover alta qualità (600x600)
            dto.albumCover = track.artworkUrl100 != null
                    ? track.artworkUrl100.replace("100x100", "600x600")
                    : "";

            dto.year = track.releaseDate != null && track.releaseDate.length() >= 4
                    ? Integer.parseInt(track.releaseDate.substring(0, 4))
                    : null;

            dto.type = type;
            dto.source = "apple-music";

            System.out.println("✅ " +
                    (type.equals("italian") ? "🇮🇹" : "🌍") +
                    " " + dto.title + " - " + dto.artist);

            return dto;

        } catch (Exception e) {
            System.err.println("❌ Apple Music error for " + artist + ": " + e.getMessage());
            return getFallbackSong(type);
        }
    }

    private MusicTrackDto getFallbackSong(String type) {
        MusicTrackDto dto = new MusicTrackDto();
        dto.source = "fallback";
        dto.type = type;

        if ("italian".equals(type)) {
            dto.id = "fallback_ita_1";
            dto.title = "Volare";
            dto.artist = "Domenico Modugno";
            dto.previewUrl = "https://audio-ssl.itunes.apple.com/itunes-assets/AudioPreview115/v4/69/f7/a7/69f7a7a6-0f4c-9d0e-3c5a-a5a6f5e3d3f3/mzaf_12345.plus.aac.p.m4a";
            dto.albumCover = "https://is1-ssl.mzstatic.com/image/thumb/Music124/v4/volare.jpg/600x600bb.jpg";
            dto.year = 1958;
        } else {
            dto.id = "fallback_int_1";
            dto.title = "Bohemian Rhapsody";
            dto.artist = "Queen";
            dto.previewUrl = "https://audio-ssl.itunes.apple.com/itunes-assets/AudioPreview125/v4/5b/2a/7a/5b2a7a3e-3cfa-7f08-f6b4-dc43d4a2c6b5/mzaf_11897835066034327923.plus.aac.p.m4a";
            dto.albumCover = "https://is1-ssl.mzstatic.com/image/thumb/Music124/v4/queen.jpg/600x600bb.jpg";
            dto.year = 1975;
        }

        System.out.println("🚑 FALLBACK: " + dto.title + " - " + dto.artist);
        return dto;
    }
}