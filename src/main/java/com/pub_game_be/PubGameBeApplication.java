package com.pub_game_be;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EntityScan("com.pub_game_be.domain")
@EnableJpaRepositories(basePackages = "com.pub_game_be.repository")
public class PubGameBeApplication {

    public static void main(String[] args) {
        SpringApplication.run(PubGameBeApplication.class, args);
    }

}
