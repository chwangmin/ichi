package com.ichi.auth.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Service;

import com.ichi.config.IchiProperties;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * 세션 토큰(JWT) 발급/검증. stateless 세션.
 * subject = google_sub. 만료는 ichi.jwt.expiration-minutes.
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationMinutes;

    public JwtService(IchiProperties props) {
        String secret = props.getJwt().getSecret();
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException(
                "JWT_SECRET 은 최소 32바이트여야 합니다 (HS256). 현재: " + bytes.length);
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.expirationMinutes = props.getJwt().getExpirationMinutes();
    }

    /** google_sub 로 토큰 발급. email/name 은 편의용 클레임. */
    public String issue(String googleSub, String email, String name) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(expirationMinutes * 60);
        return Jwts.builder()
            .subject(googleSub)
            .claim("email", email)
            .claim("name", name)
            .issuedAt(Date.from(now))
            .expiration(Date.from(exp))
            .signWith(key)
            .compact();
    }

    /** 토큰 검증 후 google_sub(subject) 반환. 유효하지 않으면 예외. */
    public String parseSubject(String token) {
        Claims claims = Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .getPayload();
        return claims.getSubject();
    }

    public long getExpirationMinutes() {
        return expirationMinutes;
    }
}
