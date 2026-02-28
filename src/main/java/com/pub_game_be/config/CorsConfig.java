package com.pub_game_be.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

@Configuration
public class CorsConfig {

    @Bean
    public CorsFilter corsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();

        // Permetti credenziali (cookies, authorization headers)
        config.setAllowCredentials(true);

        // Permetti tutti gli origin della tua rete locale + localhost
        config.addAllowedOriginPattern("http://localhost:*");
        config.addAllowedOriginPattern("http://127.0.0.1:*");
        config.addAllowedOriginPattern("http://192.168.*.*:*");
        config.addAllowedOriginPattern("http://10.*.*.*:*");
        config.addAllowedOriginPattern("https://*.trycloudflare.com");

        // Permetti tutti gli header
        config.addAllowedHeader("*");

        // Permetti tutti i metodi HTTP
        config.addAllowedMethod("*");

        // Esponi eventuali header custom
        config.addExposedHeader("Authorization");

        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}