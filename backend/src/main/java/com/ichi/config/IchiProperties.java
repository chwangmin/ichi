package com.ichi.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;

import lombok.Builder;
import lombok.Getter;

/**
 * application.yml 의 ichi.* 설정 바인딩.
 *
 * 생성자 바인딩(@ConstructorBinding)이라 setter 가 없다.
 * 코드에서 만들 때는 Lombok @Builder, 읽을 때는 @Getter 를 쓴다.
 */
@Getter
@Builder
@ConfigurationProperties(prefix = "ichi")
public class IchiProperties {

    private final Google google;
    private final Jwt jwt;
    private final Cors cors;
    private final Vworld vworld;

    @ConstructorBinding
    public IchiProperties(
            @DefaultValue Google google,
            @DefaultValue Jwt jwt,
            @DefaultValue Cors cors,
            @DefaultValue Vworld vworld) {
        this.google = google;
        this.jwt = jwt;
        this.cors = cors;
        this.vworld = vworld;
    }

    @Getter
    @Builder
    public static class Google {
        private final String clientId;
        private final String clientSecret;
        private final String redirectUri;

        public Google(
                @DefaultValue("") String clientId,
                @DefaultValue("") String clientSecret,
                @DefaultValue("http://localhost:5173/auth/callback") String redirectUri) {
            this.clientId = clientId;
            this.clientSecret = clientSecret;
            this.redirectUri = redirectUri;
        }
    }

    /** CORS 허용 origin 목록. 로컬 + Capacitor 네이티브 origin 등. */
    @Getter
    @Builder
    public static class Cors {
        private final List<String> allowedOrigins;

        public Cors(@DefaultValue("http://localhost:5173") List<String> allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
        }
    }

    @Getter
    @Builder
    public static class Jwt {
        private final String secret;
        private final long expirationMinutes;

        public Jwt(
                @DefaultValue("") String secret,
                @DefaultValue("10080") long expirationMinutes) {
            this.secret = secret;
            this.expirationMinutes = expirationMinutes;
        }
    }

    @Getter
    @Builder
    public static class Vworld {
        private final String apiKey;

        public Vworld(@DefaultValue("") String apiKey) {
            this.apiKey = apiKey;
        }
    }
}
