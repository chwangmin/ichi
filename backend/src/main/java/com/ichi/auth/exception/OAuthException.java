package com.ichi.auth.exception;

/** OAuth 흐름(코드 교환, 토큰 파싱) 중 발생하는 오류. */
public class OAuthException extends RuntimeException {

    public OAuthException(String message) {
        super(message);
    }

    public OAuthException(String message, Throwable cause) {
        super(message, cause);
    }
}
