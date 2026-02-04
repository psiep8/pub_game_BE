package com.pub_game_be.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;

@Configuration
public class RestTemplateConfig {

    /**
     * RestTemplate configurato per chiamate esterne con timeout
     */
    @Bean
    public RestTemplate restTemplate() {
        RestTemplate restTemplate = new RestTemplate();

        // Questo intercettore è VITALE per Deezer
        restTemplate.getInterceptors().add((request, body, execution) -> {
            request.getHeaders().set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            request.getHeaders().setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
            // Alcuni server richiedono anche questo per le API
            request.getHeaders().set("Accept-Language", "it-IT,it;q=0.9,en-US;q=0.8,en;q=0.7");
            return execution.execute(request, body);
        });

        return restTemplate;
    }
//    @Bean
//    public RestTemplate restTemplate() {
//        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
//
//        // Timeout connessione: 10 secondi
//        factory.setConnectTimeout(10000);
//
//        // Timeout lettura: 15 secondi
//        factory.setReadTimeout(15000);
//
//        RestTemplate restTemplate = new RestTemplate(factory);
//
//        System.out.println("✅ RestTemplate configurato con timeout 10s/15s");
//
//        return restTemplate;
//    }
}