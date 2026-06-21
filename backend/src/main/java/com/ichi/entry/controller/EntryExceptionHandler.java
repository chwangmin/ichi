package com.ichi.entry.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import com.ichi.entry.exception.EntryAccessException;
import com.ichi.storage.drive.DriveException;

/** 노트/미디어/Drive 관련 예외를 한국어 메시지로 변환. */
@RestControllerAdvice
public class EntryExceptionHandler {

    @ExceptionHandler(EntryAccessException.class)
    public ResponseEntity<Map<String, String>> handleAccess(EntryAccessException e) {
        HttpStatus status = e.isNotFound() ? HttpStatus.NOT_FOUND : HttpStatus.FORBIDDEN;
        return ResponseEntity.status(status).body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(DriveException.class)
    public ResponseEntity<Map<String, String>> handleDrive(DriveException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
            .body(Map.of("message", e.getMessage()));
    }

    /**
     * (A) Drive 호출이 403 으로 막힌 경우. 스코프 부족이면 재로그인 안내,
     * 그 외 권한 문제는 일반 메시지. 500 대신 의미 있는 응답으로 변환.
     */
    @ExceptionHandler(HttpClientErrorException.Forbidden.class)
    public ResponseEntity<Map<String, String>> handleDriveForbidden(HttpClientErrorException.Forbidden e) {
        String body = e.getResponseBodyAsString();
        boolean scopeIssue = body.contains("ACCESS_TOKEN_SCOPE_INSUFFICIENT")
            || body.contains("insufficient authentication scopes");
        if (scopeIssue) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "error", "drive_scope_missing",
                "message", "Google Drive 접근 권한이 없어요. 로그아웃 후 다시 로그인해 "
                    + "'Google Drive 앱 데이터' 권한을 허용해 주세요."));
        }
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
            .body(Map.of("message", "Google Drive 요청이 거부되었습니다."));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadInput(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
    }

    /** 업로드 용량 초과(이미지/영상). 25MB 제한을 한국어로 안내. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
            .body(Map.of("message", "파일은 25MB 이하로 업로드해 주세요."));
    }
}
