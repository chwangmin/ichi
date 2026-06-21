package com.ichi.auth.controller;

import org.springframework.http.ResponseCookie;

/** 세션 JWT 를 담는 HttpOnly 쿠키 헬퍼. */
public final class AuthCookies {

    public static final String SESSION_COOKIE = "ichi_session";

    private AuthCookies() {
    }

    /** 세션 쿠키 생성. SameSite=Lax, HttpOnly. (운영 HTTPS 에서는 secure=true 필요) */
    public static ResponseCookie session(String jwt, long maxAgeSeconds, boolean secure) {
        return ResponseCookie.from(SESSION_COOKIE, jwt)
            .httpOnly(true)
            .secure(secure)
            .path("/")
            .maxAge(maxAgeSeconds)
            .sameSite("Lax")
            .build();
    }

    /** 즉시 만료시키는 빈 쿠키 (로그아웃). */
    public static ResponseCookie expired(boolean secure) {
        return ResponseCookie.from(SESSION_COOKIE, "")
            .httpOnly(true)
            .secure(secure)
            .path("/")
            .maxAge(0)
            .sameSite("Lax")
            .build();
    }
}
