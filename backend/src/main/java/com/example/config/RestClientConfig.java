package com.example.historyq.config;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RestClientConfig {
    @Bean
    public RestClient restClient() {
        return RestClient.builder()
                .baseUrl("http://127.0.0.1:8000") // Replace with your OCR service URL;
                .build();
    }
    public String testConnection(){
        return restClient()
                .get()
                .uri("/")
                .retrieve()
                .body(String.class);
    }

}