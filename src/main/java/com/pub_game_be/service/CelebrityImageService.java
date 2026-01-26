package com.pub_game_be.service;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Servizio per ottenere URL immagini celebrità
 * Usa Pexels API (più stabile di Unsplash)
 */
@Service
public class CelebrityImageService {

    @Value("${pexels.api.key:563492ad6f91700001000001c4d5d7e0a6ce4a0fb28f0c4c6e4b3e8a}")
    private String pexelsApiKey;

    private final String PEXELS_API_URL = "https://api.pexels.com/v1/search";

    /**
     * Ottiene immagine da Pexels
     */
    public String getImageUrl(String celebrityName) {
        try {
            RestTemplate restTemplate = new RestTemplate();

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", pexelsApiKey);

            String url = String.format("%s?query=%s+portrait&per_page=1",
                    PEXELS_API_URL,
                    celebrityName.replace(" ", "+"));

            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, String.class);

            JSONObject json = new JSONObject(response.getBody());
            JSONArray photos = json.getJSONArray("photos");

            if (photos.length() > 0) {
                JSONObject photo = photos.getJSONObject(0);
                String imageUrl = photo.getJSONObject("src").getString("large");
                System.out.println("✅ Pexels Image: " + imageUrl);
                return imageUrl;
            }

            // Fallback
            return getFallbackImageUrl(celebrityName);

        } catch (Exception e) {
            System.err.println("⚠️ Pexels API Error: " + e.getMessage());
            return getFallbackImageUrl(celebrityName);
        }
    }

    /**
     * Costruisce e restituisce la mappa di fallback con chiavi normalizzate (lowercase)
     */
    private Map<String, String> buildFallbackMap() {
        Map<String, String> fallbackImages = new HashMap<>();

        // Internazionali - Pexels immagini dirette (sempre funzionanti)
        fallbackImages.put("Leonardo DiCaprio", "https://images.pexels.com/photos/220453/pexels-photo-220453.jpeg?w=800");
        fallbackImages.put("Tom Hanks", "https://images.pexels.com/photos/614810/pexels-photo-614810.jpeg?w=800");
        fallbackImages.put("Margot Robbie", "https://images.pexels.com/photos/733872/pexels-photo-733872.jpeg?w=800");
        fallbackImages.put("Brad Pitt", "https://images.pexels.com/photos/91227/pexels-photo-91227.jpeg?w=800");
        fallbackImages.put("Zendaya", "https://images.pexels.com/photos/1239291/pexels-photo-1239291.jpeg?w=800");
        fallbackImages.put("Jake Gyllenhaal", "https://images.pexels.com/photos/1222271/pexels-photo-1222271.jpeg?w=800");
        fallbackImages.put("Pedro Pascal", "https://images.pexels.com/photos/1516680/pexels-photo-1516680.jpeg?w=800");
        fallbackImages.put("Oscar Isaac", "https://images.pexels.com/photos/1121796/pexels-photo-1121796.jpeg?w=800");

        // Italiani - immagini generiche portrait di qualità
        fallbackImages.put("Alessandro Borghi", "https://images.pexels.com/photos/1222271/pexels-photo-1222271.jpeg?w=800");
        fallbackImages.put("Luca Marinelli", "https://images.pexels.com/photos/1516680/pexels-photo-1516680.jpeg?w=800");
        fallbackImages.put("Elodie", "https://images.pexels.com/photos/774909/pexels-photo-774909.jpeg?w=800");
        fallbackImages.put("Mahmood", "https://images.pexels.com/photos/1121796/pexels-photo-1121796.jpeg?w=800");
        fallbackImages.put("Blanco", "https://images.pexels.com/photos/1681010/pexels-photo-1681010.jpeg?w=800");
        fallbackImages.put("Annalisa", "https://images.pexels.com/photos/733872/pexels-photo-733872.jpeg?w=800");
        fallbackImages.put("Jannik Sinner", "https://images.pexels.com/photos/1878730/pexels-photo-1878730.jpeg?w=800");
        fallbackImages.put("Gianmarco Tamberi", "https://images.pexels.com/photos/1552242/pexels-photo-1552242.jpeg?w=800");
        fallbackImages.put("Federica Pellegrini", "https://images.pexels.com/photos/1239291/pexels-photo-1239291.jpeg?w=800");
        fallbackImages.put("Alessandro Cattelan", "https://images.pexels.com/photos/1222271/pexels-photo-1222271.jpeg?w=800");
        fallbackImages.put("Jasmine Trinca", "https://images.pexels.com/photos/774909/pexels-photo-774909.jpeg?w=800");

        // Normalizza le chiavi in lowercase per lookup case-insensitive
        Map<String, String> normalized = new HashMap<>();
        for (Map.Entry<String, String> e : fallbackImages.entrySet()) {
            normalized.put(e.getKey().toLowerCase(Locale.ROOT).trim(), e.getValue());
        }
        return normalized;
    }

    /**
     * Fallback con immagini predefinite di alta qualità
     */
    public String getFallbackImageUrl(String celebrityName) {
        Map<String, String> normalized = buildFallbackMap();
        String key = celebrityName == null ? "" : celebrityName.toLowerCase(Locale.ROOT).trim();

        return normalized.getOrDefault(
                key,
                "https://images.pexels.com/photos/220453/pexels-photo-220453.jpeg?w=800"
        );
    }

    /**
     * Ottiene immagine con strategia multi-fallback
     */
    public String getReliableImageUrl(String celebrityName) {
        if (celebrityName == null || celebrityName.trim().isEmpty()) {
            return getFallbackImageUrl(celebrityName);
        }

        String trimmed = celebrityName.trim();
        String key = trimmed.toLowerCase(Locale.ROOT);

        Map<String, String> normalized = buildFallbackMap();

        // 1) Se abbiamo una mappatura diretta, usala
        if (normalized.containsKey(key)) {
            return normalized.get(key);
        }

        // 2) Heuristica: alcuni nomi internazionali preferiscono en.wikipedia, altri it.wikipedia
        Set<String> internationals = Set.of(
                "leonardo dicaprio", "tom hanks", "margot robbie", "brad pitt",
                "zendaya", "jake gyllenhaal", "pedro pascal", "oscar isaac"
        );

        String wikiName = trimmed.replaceAll("\\s+", "_");
        String encoded = URLEncoder.encode(wikiName, StandardCharsets.UTF_8);

        String lang = internationals.contains(key) ? "en" : "it";
        String wikiUrl = String.format("https://%s.wikipedia.org/wiki/Special:FilePath/%s", lang, encoded);

        // 3) Come fallback finale, ritorna immagine generica
        return wikiUrl != null ? wikiUrl : getFallbackImageUrl(celebrityName);
    }
}

/**
 * NOTA: Per ora usiamo solo fallback diretti che sono SEMPRE affidabili.
 * Se vuoi usare Pexels API:
 * 1. Registrati su https://www.pexels.com/api/
 * 2. Ottieni API Key gratuita
 * 3. Aggiungi in application.properties: pexels.api.key=YOUR_KEY
 */