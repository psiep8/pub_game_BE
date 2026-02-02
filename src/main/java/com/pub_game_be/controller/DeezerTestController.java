//package com.pub_game_be.controller;
//
//import com.pub_game_be.dto.AppleMusicTrack;
//import com.pub_game_be.service.AppleMusicCuratorService;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.*;
//
//import java.util.HashMap;
//import java.util.Map;
//
//@RestController
//@RequestMapping("/api/test")
//public class DeezerTestController {
//
//    private final AppleMusicCuratorService deezerService;
//
//    public DeezerTestController(AppleMusicCuratorService deezerService) {
//        this.deezerService = deezerService;
//    }
//
//    /**
//     * 🧪 Test Deezer - Canzone random (70/30 split)
//     *
//     * GET http://localhost:8080/api/test/deezer
//     */
//    @GetMapping("/deezer")
//    public ResponseEntity<Map<String, Object>> testDeezer() {
//        try {
//            AppleMusicTrack song = deezerService.getFamousSong();
//
//            Map<String, Object> response = new HashMap<>();
//            response.put("success", true);
//            response.put("song", song);
//
//            return ResponseEntity.ok(response);
//
//        } catch (Exception e) {
//            Map<String, Object> errorResponse = new HashMap<>();
//            errorResponse.put("success", false);
//            errorResponse.put("error", e.getMessage());
//
//            return ResponseEntity.status(500).body(errorResponse);
//        }
//    }
//
//    /**
//     * 🇮🇹 Test canzone italiana
//     *
//     * GET http://localhost:8080/api/test/deezer/italian
//     */
//    @GetMapping("/deezer/italian")
//    public ResponseEntity<Map<String, Object>> testItalian() {
//        try {
//            AppleMusicTrack song = deezerService.getItalianSong();
//
//            Map<String, Object> response = new HashMap<>();
//            response.put("success", true);
//            response.put("song", song);
//            response.put("type", "italian");
//
//            return ResponseEntity.ok(response);
//
//        } catch (Exception e) {
//            Map<String, Object> errorResponse = new HashMap<>();
//            errorResponse.put("success", false);
//            errorResponse.put("error", e.getMessage());
//
//            return ResponseEntity.status(500).body(errorResponse);
//        }
//    }
//
//    /**
//     * 🌍 Test canzone internazionale
//     *
//     * GET http://localhost:8080/api/test/deezer/international
//     */
//    @GetMapping("/deezer/international")
//    public ResponseEntity<Map<String, Object>> testInternational() {
//        try {
//            AppleMusicTrack song = deezerService.getInternationalSong();
//
//            Map<String, Object> response = new HashMap<>();
//            response.put("success", true);
//            response.put("song", song);
//            response.put("type", "international");
//
//            return ResponseEntity.ok(response);
//
//        } catch (Exception e) {
//            Map<String, Object> errorResponse = new HashMap<>();
//            errorResponse.put("success", false);
//            errorResponse.put("error", e.getMessage());
//
//            return ResponseEntity.status(500).body(errorResponse);
//        }
//    }
//
//    /**
//     * 📊 Test multipli per verificare split 70/30
//     *
//     * GET http://localhost:8080/api/test/deezer/stats?count=20
//     */
//    @GetMapping("/deezer/stats")
//    public ResponseEntity<Map<String, Object>> testStats(@RequestParam(defaultValue = "10") int count) {
//        try {
//            int italian = 0;
//            int international = 0;
//
//            for (int i = 0; i < count; i++) {
//                AppleMusicTrack song = deezerService.getFamousSong();
//                if ("italian".equals(song.type)) {
//                    italian++;
//                } else {
//                    international++;
//                }
//            }
//
//            Map<String, Object> response = new HashMap<>();
//            response.put("success", true);
//            response.put("totalTests", count);
//            response.put("italian", italian);
//            response.put("international", international);
//            response.put("italianPercentage", (italian * 100.0) / count);
//            response.put("internationalPercentage", (international * 100.0) / count);
//
//            return ResponseEntity.ok(response);
//
//        } catch (Exception e) {
//            Map<String, Object> errorResponse = new HashMap<>();
//            errorResponse.put("success", false);
//            errorResponse.put("error", e.getMessage());
//
//            return ResponseEntity.status(500).body(errorResponse);
//        }
//    }
//}