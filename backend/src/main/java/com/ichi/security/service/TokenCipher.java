package com.ichi.security.service;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * refresh_token 등 민감 컬럼의 암호화/복호화.
 *
 * - ichi.token-enc-key(TOKEN_ENC_KEY, base64 32바이트)가 설정되면 AES-256-GCM 사용.
 * - 비어 있으면 개발 모드로 간주해 평문 그대로 저장하고 경고 로그를 남긴다.
 *   (§10: 운영에서는 반드시 키를 설정할 것)
 *
 * 저장 형식: "enc:v1:" + base64(iv || ciphertext+tag)
 *   평문은 접두어 없이 저장되며, 복호화 시 접두어 유무로 분기한다.
 */
@Component
public class TokenCipher {

    private static final Logger log = LoggerFactory.getLogger(TokenCipher.class);
    private static final String PREFIX = "enc:v1:";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;       // GCM 권장 96비트
    private static final int TAG_LENGTH_BITS = 128;

    private final SecretKeySpec key; // null 이면 평문 모드
    private final SecureRandom random = new SecureRandom();

    public TokenCipher(@Value("${ichi.token-enc-key:}") String base64Key) {
        if (!StringUtils.hasText(base64Key)) {
            this.key = null;
            log.warn("TOKEN_ENC_KEY 미설정 — refresh_token 을 평문으로 저장합니다. "
                + "운영 환경에서는 반드시 TOKEN_ENC_KEY 를 설정하세요.");
            return;
        }
        byte[] raw = Base64.getDecoder().decode(base64Key.trim());
        if (raw.length != 16 && raw.length != 24 && raw.length != 32) {
            throw new IllegalStateException(
                "TOKEN_ENC_KEY 는 base64 디코딩 시 16/24/32 바이트여야 합니다. 현재: " + raw.length);
        }
        this.key = new SecretKeySpec(raw, "AES");
        log.info("TokenCipher: AES-GCM 암호화 활성화 ({}바이트 키).", raw.length);
    }

    /** 평문을 저장용 문자열로. 키 없으면 평문 그대로 반환. */
    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        if (key == null) {
            return plaintext;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("refresh_token 암호화 실패", e);
        }
    }

    /** 저장된 문자열을 평문으로. 접두어가 없으면 평문으로 간주해 그대로 반환. */
    public String decrypt(String stored) {
        if (stored == null) {
            return null;
        }
        if (!stored.startsWith(PREFIX)) {
            return stored; // 평문(키 미설정 시절에 저장된 값 포함)
        }
        if (key == null) {
            throw new IllegalStateException(
                "암호화된 refresh_token 을 복호화할 수 없습니다. TOKEN_ENC_KEY 가 설정되지 않았습니다.");
        }
        try {
            byte[] all = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            byte[] iv = new byte[IV_LENGTH];
            byte[] ct = new byte[all.length - IV_LENGTH];
            System.arraycopy(all, 0, iv, 0, IV_LENGTH);
            System.arraycopy(all, IV_LENGTH, ct, 0, ct.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("refresh_token 복호화 실패", e);
        }
    }
}
