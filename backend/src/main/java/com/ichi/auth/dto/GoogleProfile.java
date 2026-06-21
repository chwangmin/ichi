package com.ichi.auth.dto;

/**
 * Google OIDC id_token 에서 추출한 사용자 프로필 + Drive 호출용 refresh_token.
 * refreshToken 은 평문이며, 저장 전에 TokenCipher 로 암호화한다.
 */
public record GoogleProfile(
    String sub,
    String email,
    String name,
    String pictureUrl,
    String refreshToken,
    String grantedScopes // 토큰 응답의 scope (공백 구분). 권한 검증용.
) {
    /**
     * 일기 저장에 필요한 Drive 권한(drive.file)이 부여됐는지.
     * 프론트 동의 요청 scope(auth/google.ts)와 반드시 일치해야 한다.
     */
    public boolean hasDriveScope() {
        return grantedScopes != null
            && grantedScopes.contains("https://www.googleapis.com/auth/drive.file");
    }
}
