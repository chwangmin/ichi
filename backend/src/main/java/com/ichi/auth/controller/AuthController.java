package com.ichi.auth.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ichi.auth.dto.GoogleLoginRequest;
import com.ichi.auth.dto.UserResponse;
import com.ichi.auth.service.AuthService;
import com.ichi.auth.service.JwtService;
import com.ichi.user.repository.UserRepository;

import jakarta.validation.Valid;

/**
 * 인증 엔드포인트 (§5).
 *  POST /api/auth/google  authorization code → users upsert + 세션 쿠키 발급
 *  GET  /api/auth/me      현재 로그인 사용자
 *  POST /api/auth/logout  세션 쿠키 만료
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;
    private final UserRepository users;

    /** 운영(HTTPS)에서는 true. 로컬 http 개발에서는 false. */
    @Value("${ichi.cookie-secure:false}")
    private boolean cookieSecure;

    public AuthController(AuthService authService, JwtService jwtService, UserRepository users) {
        this.authService = authService;
        this.jwtService = jwtService;
        this.users = users;
    }

    @PostMapping("/google")
    public ResponseEntity<UserResponse> google(@Valid @RequestBody GoogleLoginRequest req) {
        AuthService.LoginResult result = authService.login(req.code());

        long maxAge = jwtService.getExpirationMinutes() * 60L;
        ResponseCookie cookie = AuthCookies.session(result.jwt(), maxAge, cookieSecure);

        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, cookie.toString())
            .body(UserResponse.from(result.user()));
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(@AuthenticationPrincipal String googleSub) {
        if (googleSub == null) {
            return ResponseEntity.status(401).build();
        }
        return users.findById(googleSub)
            .map(UserResponse::from)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.status(401).build());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        ResponseCookie expired = AuthCookies.expired(cookieSecure);
        return ResponseEntity.noContent()
            .header(HttpHeaders.SET_COOKIE, expired.toString())
            .build();
    }
}
