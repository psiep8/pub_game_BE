package com.pub_game_be.service;

import com.pub_game_be.dto.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Random;

@Service
public class DeezerCuratorService {

    private final RestTemplate restTemplate;
    private final Random random = new Random();

    private static final String API_URL = "https://api.deezer.com";

    // 🇮🇹 Playlist italiane (testate e stabili)
    private static final List<String> ITALIAN_PLAYLISTS = List.of(
            "1479165185", // Pop Italiano
            "1109946311", // Classici Italiani
            "8672307782", // Hit Italiane
            "8223293942"  // Italo Hits
    );

    // 🌍 Playlist internazionali (testate)
    private static final List<String> INTERNATIONAL_PLAYLISTS = List.of(
            "1313621735", // Global Top
            "1266970301", // Rock Classics
            "1677006641", // All Out 80s
            "1282483245", // All Out 90s
            "1116885485", // All Out 2000s
            "1109890211"  // Classic Hits Global
    );

    public DeezerCuratorService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    // 🎯 ENTRY POINT
    public MusicTrackDto getFamousSong() {
        boolean italian = random.nextDouble() < 0.7;
        System.out.println("🎵 Deezer split: " + (italian ? "🇮🇹 ITA" : "🌍 INT"));
        return italian ? getItalianSong() : getInternationalSong();
    }

    public MusicTrackDto getItalianSong() {
        return getFromPlaylist(pickRandom(ITALIAN_PLAYLISTS), "italian");
    }

    public MusicTrackDto getInternationalSong() {
        return getFromPlaylist(pickRandom(INTERNATIONAL_PLAYLISTS), "international");
    }

    // 🔥 Playlist → Chart → Fallback
    private MusicTrackDto getFromPlaylist(String playlistId, String type) {
        try {
            System.out.println("🔗 Deezer playlist " + playlistId);

            DeezerPlaylistResponse response = restTemplate.getForObject(
                    API_URL + "/playlist/" + playlistId + "/tracks?limit=100",
                    DeezerPlaylistResponse.class
            );

            if (response == null || response.data == null || response.data.isEmpty()) {
                throw new RuntimeException("Empty playlist");
            }

            return mapRandomTrack(response.data, type);

        } catch (Exception e) {
            System.err.println("⚠️ Playlist failed → fallback chart");
            return getFromChart(type);
        }
    }

    // 📊 Chart fallback (super affidabile)
    private MusicTrackDto getFromChart(String type) {
        try {
            DeezerPlaylistResponse response = restTemplate.getForObject(
                    API_URL + "/chart/0/tracks?limit=50",
                    DeezerPlaylistResponse.class
            );

            if (response == null || response.data == null || response.data.isEmpty()) {
                throw new RuntimeException("Empty chart");
            }

            return mapRandomTrack(response.data, type);

        } catch (Exception e) {
            System.err.println("🚨 Chart failed → hard fallback");
            return hardFallback(type);
        }
    }

    // 🎯 Mapping + filtro preview
    private MusicTrackDto mapRandomTrack(List<DeezerTrackDto> tracks, String type) {
        List<DeezerTrackDto> valid = tracks.stream()
                .filter(t ->
                        t != null &&
                                t.preview != null &&
                                !t.preview.isEmpty() &&
                                t.artist != null &&
                                t.artist.name != null
                )
                .toList();

        if (valid.isEmpty()) {
            throw new RuntimeException("No valid tracks with preview");
        }

        DeezerTrackDto t = valid.get(random.nextInt(valid.size()));

        MusicTrackDto dto = new MusicTrackDto();
        dto.id = String.valueOf(t.id);
        dto.title = t.title;
        dto.artist = t.artist.name;
        dto.previewUrl = t.preview;
        dto.albumCover = t.album != null
                ? (t.album.cover_xl != null ? t.album.cover_xl : t.album.cover_big)
                : null;
        dto.year = (t.album != null && t.album.release_date != null && t.album.release_date.length() >= 4)
                ? Integer.parseInt(t.album.release_date.substring(0, 4))
                : null;
        dto.type = type;
        dto.source = "deezer";

        System.out.println("✅ Deezer OK: " + dto.title + " - " + dto.artist);
        return dto;
    }

    // 🧯 Ultima difesa: MAI crashare il gioco
    private MusicTrackDto hardFallback(String type) {
        MusicTrackDto dto = new MusicTrackDto();
        dto.source = "fallback";
        dto.type = type;

        if ("italian".equals(type)) {
            dto.id = "fallback_ita";
            dto.title = "Sarà perché ti amo";
            dto.artist = "Ricchi e Poveri";
            dto.previewUrl =
                    "https://cdns-preview-d.dzcdn.net/stream/c-d0119e09641755109b82098b0304a39b-4.mp3";
        } else {
            dto.id = "fallback_int";
            dto.title = "Blinding Lights";
            dto.artist = "The Weeknd";
            dto.previewUrl =
                    "https://cdns-preview-b.dzcdn.net/stream/c-babb6bdd0c6446777085775193910f54-5.mp3";
        }

        System.err.println("🚑 FALLBACK USATO");
        return dto;
    }

    private String pickRandom(List<String> list) {
        return list.get(random.nextInt(list.size()));
    }
}
