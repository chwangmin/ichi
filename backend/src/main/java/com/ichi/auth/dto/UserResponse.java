package com.ichi.auth.dto;

import com.ichi.user.domain.User;

/** 프론트에 노출하는 사용자 정보 (refresh_token 등 민감값 제외). */
public record UserResponse(
    String email,
    String name,
    String pictureUrl
) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getEmail(), user.getName(), user.getPictureUrl());
    }
}
