package com.ichi.user.domain;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자 계정 (Google OIDC). §4 users 테이블과 1:1.
 * refresh_token 은 Drive 호출용이며 TokenCipher 로 암호화되어 저장될 수 있다.
 *
 * Setter 대신 의미 있는 도메인 메서드로만 상태를 바꾼다.
 */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA 용
@AllArgsConstructor
public class User {

    @Id
    @Column(name = "google_sub")
    private String googleSub;

    @Column(nullable = false)
    private String email;

    private String name;

    @Column(name = "picture_url")
    private String pictureUrl;

    /** 암호화되어 저장될 수 있음 (TokenCipher). 평문으로 다루지 말 것. */
    @Column(name = "refresh_token")
    private String refreshToken;

    /** Drive 상의 "ichi" 폴더 ID. null 이면 첫 요청 시 자동 생성. */
    @Column(name = "ichi_folder_id")
    private String ichiFolder;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    /** 신규 가입/프로필용 생성자. created_at 은 DB default. */
    public User(String googleSub, String email, String name, String pictureUrl) {
        this.googleSub = googleSub;
        this.email = email;
        this.name = name;
        this.pictureUrl = pictureUrl;
    }

    /** 로그인 때마다 최신 프로필로 갱신. */
    public void updateProfile(String email, String name, String pictureUrl) {
        this.email = email;
        this.name = name;
        this.pictureUrl = pictureUrl;
    }

    /**
     * refresh_token 갱신. Google 은 재로그인 시 refresh_token 을 항상 주지 않으므로
     * null/blank 면 기존 값을 유지한다.
     */
    public void updateRefreshToken(String encryptedRefreshToken) {
        if (encryptedRefreshToken != null && !encryptedRefreshToken.isBlank()) {
            this.refreshToken = encryptedRefreshToken;
        }
    }

    /** Drive 상의 "ichi" 폴더 ID 저장. */
    public void updateIchiFolder(String folderId) {
        this.ichiFolder = folderId;
    }
}
