package com.tech.techkuppiapp.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI techKuppiOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Tech Kuppi API")
                        .description("REST API for Tech Kuppi App – question bank, batch generation, leaderboard, and Telegram integration.")
                        .version("1.0"));
    }
}
