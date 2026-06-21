package com.ichi.auth.service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import com.ichi.auth.dto.GoogleProfile;
import com.ichi.auth.exception.OAuthException;
import com.ichi.config.IchiProperties;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Google OAuth 2.0 / OIDC.
 * 프론트가 받은 authorization code 를 백엔드에서 token 으로 교환하고(§5),
 * id_token 에서 사용자 프로필을, 응답에서 Drive 호출용 refresh_token 을 꺼낸다.
 */
@Service
public class GoogleOAuthService {

    private static final String TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token";

    private final IchiProperties props;
    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GoogleOAuthService(IchiProperties props, RestClient.Builder builder) {
        this.props = props;
        this.restClient = builder.build();
    }

    /**
     * authorization code → 토큰 교환 → 프로필 + refresh_token 반환.
     */
    public GoogleProfile exchangeCode(String code) {
        var google = props.getGoogle();

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("code", code);
        form.add("client_id", google.getClientId());
        form.add("client_secret", google.getClientSecret());
        form.add("redirect_uri", google.getRedirectUri());
        form.add("grant_type", "authorization_code");

        @SuppressWarnings("unchecked")
        Map<String, Object> token = restClient.post()
            .uri(TOKEN_ENDPOINT)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(form)
            .retrieve()
            .body(Map.class);

        if (token == null || token.get("id_token") == null) {
            throw new OAuthException("Google 토큰 교환에 실패했습니다.");
        }

        String idToken = (String) token.get("id_token");
        Object refreshToken = token.get("refresh_token"); // 최초 동의 때만 내려올 수 있음
        Object scope = token.get("scope");                // 부여된 스코프(공백 구분)

        JsonNode claims = decodeIdTokenClaims(idToken);

        return new GoogleProfile(
            claims.path("sub").asString(),
            claims.path("email").asString(),
            claims.path("name").asString(null),
            claims.path("picture").asString(null),
            refreshToken == null ? null : refreshToken.toString(),
            scope == null ? null : scope.toString()
        );
    }

    /**
     * id_token(JWT)의 payload 를 디코딩한다.
     * 토큰은 Google 의 token 엔드포인트에서 TLS 로 직접 받은 것이므로 신뢰한다.
     * (서명 검증은 M2 범위에서 생략. 추후 JWKS 검증을 붙일 수 있음 — TODO.)
     */
    private JsonNode decodeIdTokenClaims(String idToken) {
        try {
            String[] parts = idToken.split("\\.");
            if (parts.length < 2) {
                throw new OAuthException("id_token 형식이 올바르지 않습니다.");
            }
            byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
            return objectMapper.readTree(new String(payload, StandardCharsets.UTF_8));
        } catch (OAuthException e) {
            throw e;
        } catch (Exception e) {
            throw new OAuthException("id_token 파싱에 실패했습니다.", e);
        }
    }
}
