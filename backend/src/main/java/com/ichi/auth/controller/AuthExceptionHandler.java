package com.ichi.auth.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.ichi.auth.exception.DriveScopeMissingException;
import com.ichi.auth.exception.OAuthException;

/** 인증 관련 예외를 한국어 메시지로 변환. */
@RestControllerAdvice
public class AuthExceptionHandler {

    @ExceptionHandler(OAuthException.class)
    public ResponseEntity<Map<String, String>> handleOAuth(OAuthException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(Map.of("error", "google_oauth_failed", "message", e.getMessage()));
    }

    /** Drive 권한 미동의 → 403 + 전용 코드(프론트가 재로그인 안내). */
    @ExceptionHandler(DriveScopeMissingException.class)
    public ResponseEntity<Map<String, String>> handleDriveScope(DriveScopeMissingException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(Map.of("error", "drive_scope_missing", "message", e.getMessage()));
    }
}
