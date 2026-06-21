package com.ichi.auth.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ichi.auth.dto.GoogleProfile;
import com.ichi.auth.exception.DriveScopeMissingException;
import com.ichi.security.service.TokenCipher;
import com.ichi.user.domain.User;
import com.ichi.user.repository.UserRepository;

/**
 * 로그인 처리: Google 코드 교환 → users upsert(프로필/refresh_token) → JWT 발급.
 */
@Service
public class AuthService {

    private final GoogleOAuthService googleOAuth;
    private final UserRepository users;
    private final TokenCipher tokenCipher;
    private final JwtService jwtService;

    public AuthService(GoogleOAuthService googleOAuth, UserRepository users,
            TokenCipher tokenCipher, JwtService jwtService) {
        this.googleOAuth = googleOAuth;
        this.users = users;
        this.tokenCipher = tokenCipher;
        this.jwtService = jwtService;
    }

    /**
     * authorization code 로 로그인하고 세션 JWT 를 반환한다.
     * 회원 정보(users)는 여기서 저장/갱신된다.
     */
    @Transactional
    public LoginResult login(String code) {
        GoogleProfile profile = googleOAuth.exchangeCode(code);

        // (B) 입구 검증: 이치는 본문을 사용자 Drive 에 저장하므로 drive.file 권한이 필수.
        // 사용자가 동의 화면에서 Drive 권한을 거부했으면 로그인 자체를 막는다.
        if (!profile.hasDriveScope()) {
            throw new DriveScopeMissingException();
        }

        // refresh_token 은 암호화해 저장 (없으면 기존 값 유지)
        String encryptedRefresh = tokenCipher.encrypt(profile.refreshToken());

        User user = users.findById(profile.sub())
            .map(existing -> {
                existing.updateProfile(profile.email(), profile.name(), profile.pictureUrl());
                existing.updateRefreshToken(encryptedRefresh);
                return existing;
            })
            .orElseGet(() -> {
                User created = new User(profile.sub(), profile.email(), profile.name(), profile.pictureUrl());
                created.updateRefreshToken(encryptedRefresh);
                return created;
            });
        users.save(user);

        String jwt = jwtService.issue(user.getGoogleSub(), user.getEmail(), user.getName());
        return new LoginResult(jwt, user);
    }

    /** 발급된 JWT + 저장된 사용자. */
    public record LoginResult(String jwt, User user) {
    }
}
