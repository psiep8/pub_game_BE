package com.pub_game_be.service;

import com.pub_game_be.dto.AppleMusicResponse;
import com.pub_game_be.dto.AppleMusicTrack;
import com.pub_game_be.dto.MusicTrackDto;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;

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

    // 🇮🇹 ARTISTI ITALIANI ULTRA-FAMOSI (70%)
    private static final List<String> ITALIAN_ARTISTS = List.of(
            // Leggende assolute
            "Vasco Rossi", "Ligabue", "Zucchero", "Lucio Battisti",
            "Eros Ramazzotti", "Laura Pausini", "Tiziano Ferro",
            "Gianna Nannini", "Renato Zero", "Claudio Baglioni",
            "Lucio Dalla", "Adriano Celentano", "Domenico Modugno",

            // Pop contemporaneo MAINSTREAM
            "Marco Mengoni", "Giorgia", "Elisa", "Alessandra Amoroso",
            "Emma Marrone", "Annalisa", "Elodie", "Mahmood",

            // Sanremo & Radio
            "Max Pezzali", "883", "Nek", "Subsonica",

            // Dance italiana ICONICA
            "Gigi D'Agostino", "Gabry Ponte", "Eiffel 65"
    );

    // 🌍 ARTISTI INTERNAZIONALI ULTRA-FAMOSI (30%)
    private static final List<String> INTERNATIONAL_ARTISTS = List.of(
            // Leggende immortali
            "Queen", "The Beatles", "Michael Jackson", "Madonna",
            "ABBA", "Whitney Houston", "Celine Dion",

            // Rock iconico
            "Coldplay", "U2", "Bon Jovi", "The Rolling Stones",
            "Eagles", "Guns N' Roses", "Nirvana", "Oasis",
            "Led Zeppelin", "Pink Floyd", "AC/DC",

            // Pop MONDIALE
            "Ed Sheeran", "Adele", "Bruno Mars", "Taylor Swift",
            "The Weeknd", "Ariana Grande", "Justin Timberlake",
            "Lady Gaga", "Beyoncé", "Rihanna",

            // Dance MAINSTREAM
            "Daft Punk", "David Guetta", "Calvin Harris", "Avicii",
            "The Chainsmokers",

            // Hip Hop ICONICO
            "Eminem", "50 Cent", "Jay-Z", "Snoop Dogg"
    );

    public AppleMusicCuratorService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * 🎵 Ottieni canzone famosa - 70% italiana, 30% internazionale
     */
    public MusicTrackDto getFamousSong() {
        boolean italian = random.nextDouble() < 0.7;
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
     * Ordina per popolarità e prende la prima canzone disponibile
     */
    private MusicTrackDto searchTopSongByArtist(String artist, String type) {
        try {
            String query = URLEncoder.encode(artist, StandardCharsets.UTF_8);

            // 🎯 Cerca con limite 50 per avere più scelta
            String url = API_URL + "?term=" + query +
                    "&entity=song&limit=50&country=IT";

            System.out.println("🔍 Searching Apple Music: " + artist);

            String rawJson = restTemplate.getForObject(url, String.class);
            AppleMusicResponse response = objectMapper.readValue(rawJson, AppleMusicResponse.class);

            if (response == null || response.results == null || response.results.isEmpty()) {
                System.out.println("⚠️ No results for " + artist + ", using fallback");
                return getFallbackSong(type);
            }

            // 🔥 FILTRA:
            // 1. Solo canzoni CON PREVIEW
            // 2. Solo canzoni dell'artista cercato (no featuring/compilation)
            // 3. Preferenza per anno >= 1970 (più riconoscibili)
            List<AppleMusicTrack> validTracks = response.results.stream()
                    .filter(t -> t.previewUrl != null && !t.previewUrl.isEmpty())
                    .filter(t -> t.artistName != null &&
                            t.artistName.toLowerCase().contains(artist.toLowerCase().split(" ")[0]))
                    .filter(t -> {
                        if (t.releaseDate == null || t.releaseDate.length() < 4) return true;
                        try {
                            int year = Integer.parseInt(t.releaseDate.substring(0, 4));
                            return year >= 1970; // Solo dal 1970 in poi
                        } catch (Exception e) {
                            return true;
                        }
                    })
                    .toList();

            if (validTracks.isEmpty()) {
                System.out.println("⚠️ No valid tracks for " + artist + ", using fallback");
                return getFallbackSong(type);
            }

            // 🎯 PRENDI LA PRIMA (più popolare)
            AppleMusicTrack track = validTracks.get(0);

            MusicTrackDto dto = new MusicTrackDto();
            dto.id = String.valueOf(track.trackId);
            dto.title = track.trackName;
            dto.artist = track.artistName;
            dto.previewUrl = track.previewUrl;

            // Cover alta qualità (600x600)
            dto.albumCover = track.artworkUrl100 != null
                    ? track.artworkUrl100.replace("100x100", "600x600")
                    : "";

            // Anno
            if (track.releaseDate != null && track.releaseDate.length() >= 4) {
                try {
                    dto.year = Integer.parseInt(track.releaseDate.substring(0, 4));
                } catch (Exception e) {
                    dto.year = null;
                }
            }

            dto.duration = 30;
            dto.type = type;
            dto.source = "apple-music";

            System.out.println("✅ " +
                    (type.equals("italian") ? "🇮🇹" : "🌍") +
                    " " + dto.title + " - " + dto.artist +
                    (dto.year != null ? " (" + dto.year + ")" : ""));

            return dto;

        } catch (Exception e) {
            System.err.println("❌ Apple Music error for " + artist + ": " + e.getMessage());
            e.printStackTrace();
            return getFallbackSong(type);
        }
    }

    /**
     * 🚑 FALLBACK con hit ICONICHE garantite
     */
    private MusicTrackDto getFallbackSong(String type) {
        MusicTrackDto dto = new MusicTrackDto();
        dto.source = "fallback";
        dto.type = type;
        dto.duration = 30;

        if ("italian".equals(type)) {
            // Fallback italiani ICONICI
            String[][] italianHits = {
                    {"fallback_ita_1", "Albachiara", "Vasco Rossi", "1979"},
                    {"fallback_ita_2", "Sally", "Vasco Rossi", "1996"},
                    {"fallback_ita_3", "Volare", "Domenico Modugno", "1958"},
                    {"fallback_ita_4", "La solitudine", "Laura Pausini", "1993"},
                    {"fallback_ita_5", "Azzurro", "Adriano Celentano", "1968"},
                    {"fallback_ita_6", "Un'emozione per sempre", "Eros Ramazzotti", "2003"},
                    {"fallback_ita_7", "Caruso", "Lucio Dalla", "1986"}
            };

            String[] hit = italianHits[random.nextInt(italianHits.length)];
            dto.id = hit[0];
            dto.title = hit[1];
            dto.artist = hit[2];
            dto.year = Integer.parseInt(hit[3]);
            dto.previewUrl = ""; // Frontend userà Spotify come fallback
            dto.albumCover = "";

        } else {
            // Fallback internazionali ICONICI
            String[][] internationalHits = {
                    {"fallback_int_1", "Bohemian Rhapsody", "Queen", "1975"},
                    {"fallback_int_2", "Billie Jean", "Michael Jackson", "1982"},
                    {"fallback_int_3", "Thriller", "Michael Jackson", "1982"},
                    {"fallback_int_4", "Like a Prayer", "Madonna", "1989"},
                    {"fallback_int_5", "Hotel California", "Eagles", "1976"},
                    {"fallback_int_6", "Sweet Child O' Mine", "Guns N' Roses", "1987"},
                    {"fallback_int_7", "Smells Like Teen Spirit", "Nirvana", "1991"},
                    {"fallback_int_8", "Wonderwall", "Oasis", "1995"},
                    {"fallback_int_9", "Dancing Queen", "ABBA", "1976"},
                    {"fallback_int_10", "Imagine", "John Lennon", "1971"}
            };

            String[] hit = internationalHits[random.nextInt(internationalHits.length)];
            dto.id = hit[0];
            dto.title = hit[1];
            dto.artist = hit[2];
            dto.year = Integer.parseInt(hit[3]);
            dto.previewUrl = ""; // Frontend userà Spotify come fallback
            dto.albumCover = "";
        }

        System.out.println("🚑 FALLBACK: " + dto.title + " - " + dto.artist + " (" + dto.year + ")");
        return dto;
    }
}