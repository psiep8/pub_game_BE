package com.pub_game_be.service;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Servizio per ottenere immagini celebrità da TMDB (The Movie Database)
 * API GRATUITA: https://www.themoviedb.org/settings/api
 */
@Service
public class TMDBImageService {

    @Value("${tmdb.api.key}")
    private String tmdbApiKey;

    private final String TMDB_SEARCH_URL = "https://api.themoviedb.org/3/search/person";
    private final String TMDB_IMAGE_BASE = "https://image.tmdb.org/t/p/w500";

    /**
     * Cerca celebrità su TMDB e restituisce URL immagine profilo
     */
    public String getCelebrityImageUrl(String celebrityName) {
        try {
            RestTemplate restTemplate = new RestTemplate();

            String encodedName = URLEncoder.encode(celebrityName, StandardCharsets.UTF_8);
            String url = String.format("%s?api_key=%s&query=%s&language=it-IT",
                    TMDB_SEARCH_URL, tmdbApiKey, encodedName);

            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            JSONObject json = new JSONObject(response.getBody());
            JSONArray results = json.getJSONArray("results");

            if (results.length() > 0) {
                JSONObject person = results.getJSONObject(0);
                String profilePath = person.optString("profile_path", null);

                if (profilePath != null && !profilePath.isEmpty()) {
                    String imageUrl = TMDB_IMAGE_BASE + profilePath;
                    System.out.println("✅ TMDB Image per " + celebrityName + ": " + imageUrl);
                    return imageUrl;
                }
            }

            System.out.println("⚠️ Nessuna immagine trovata su TMDB per: " + celebrityName);
            return null;

        } catch (Exception e) {
            System.err.println("❌ Errore TMDB API: " + e.getMessage());
            return null;
        }
    }

    /**
     * Ottiene info complete celebrità (nome, popolarità, immagine)
     */
    public JSONObject getCelebrityInfo(String celebrityName) {
        try {
            RestTemplate restTemplate = new RestTemplate();

            String encodedName = URLEncoder.encode(celebrityName, StandardCharsets.UTF_8);
            String url = String.format("%s?api_key=%s&query=%s&language=it-IT",
                    TMDB_SEARCH_URL, tmdbApiKey, encodedName);

            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            JSONObject json = new JSONObject(response.getBody());
            JSONArray results = json.getJSONArray("results");

            if (results.length() > 0) {
                JSONObject person = results.getJSONObject(0);
                String profilePath = person.optString("profile_path", null);
                String imageUrl = profilePath != null ? TMDB_IMAGE_BASE + profilePath : null;

                JSONObject info = new JSONObject();
                info.put("name", person.getString("name"));
                info.put("popularity", person.getDouble("popularity"));
                info.put("imageUrl", imageUrl);
                info.put("knownFor", person.optString("known_for_department", "Unknown"));

                return info;
            }

            return null;

        } catch (Exception e) {
            System.err.println("❌ Errore TMDB API: " + e.getMessage());
            return null;
        }
    }
}