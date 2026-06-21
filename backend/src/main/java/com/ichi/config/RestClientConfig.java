package com.ichi.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * RestClient.Builder 빈 제공.
 * Spring Boot 4 에서는 starter-web 만으로 RestClient.Builder 가 자동 등록되지 않는 경우가 있어
 * (GoogleOAuthService·GoogleDriveClient 가 주입받음) 명시적으로 정의한다.
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}
