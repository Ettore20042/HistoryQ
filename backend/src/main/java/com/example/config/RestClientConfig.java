
package com.example.historyq.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.client.RestClient;

@EnableAsync
@Configuration
public class RestClientConfig {

    @Value("${ocr.service.url}")
    private String ocrServiceUrl;

    @Value("${ocr.service.connect-timeout:10s}")
    private Duration connectTimeout;

    @Value("${ocr.service.read-timeout:30m}")
    private Duration readTimeout;

    @Bean
    public RestClient restClient() {
        SimpleClientHttpRequestFactory requestFactory =
                new SimpleClientHttpRequestFactory();

        requestFactory.setConnectTimeout(
                Math.toIntExact(connectTimeout.toMillis())
        );

        requestFactory.setReadTimeout(
                Math.toIntExact(readTimeout.toMillis())
        );

        return RestClient.builder()
                .baseUrl(ocrServiceUrl)
                .requestFactory(requestFactory)
                .build();
    }
}
