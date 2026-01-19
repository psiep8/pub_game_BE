package com.pub_game_be;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.web.bind.annotation.CrossOrigin;

@CrossOrigin(origins = "http://localhost:4200")
@SpringBootApplication
@EntityScan("com.pub_game_be.domain")
public class PubGameBeApplication {

    public static void main(String[] args) {
        SpringApplication.run(PubGameBeApplication.class, args);
    }

}
