package com.example.historyq;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
public class HistoryQApplication {

    public static void main(String[] args) {
        SpringApplication.run(HistoryQApplication.class, args);
    }
}
