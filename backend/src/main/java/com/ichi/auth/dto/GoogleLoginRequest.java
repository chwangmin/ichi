package com.ichi.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** 프론트가 Google 에서 받은 authorization code 를 백엔드로 전달. */
public record GoogleLoginRequest(
    @NotBlank(message = "authorization code 가 필요합니다.") String code
) {
}
