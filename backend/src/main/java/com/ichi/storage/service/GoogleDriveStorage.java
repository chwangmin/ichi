package com.ichi.storage.service;

import org.springframework.stereotype.Service;

import com.ichi.security.service.TokenCipher;
import com.ichi.storage.drive.DriveException;
import com.ichi.storage.drive.GoogleDriveClient;
import com.ichi.user.domain.User;
import com.ichi.user.repository.UserRepository;

/**
 * StorageService 의 Google Drive 구현 (drive.file 스코프, "ichi" 폴더 트리).
 *
 * userId(google_sub) 로 사용자의 refresh_token 을 찾아 복호화 → 액세스 토큰 갱신 →
 * Drive v3 호출. 본문/미디어가 모두 이 한 곳을 통과한다.
 *
 * 향후 EncryptedStorage 가 이 구현을 delegate 로 감싸 content 를 암호화할 수 있다.
 */
@Service
public class GoogleDriveStorage implements StorageService {

    private final UserRepository users;
    private final TokenCipher tokenCipher;
    private final GoogleDriveClient drive;

    public GoogleDriveStorage(UserRepository users, TokenCipher tokenCipher, GoogleDriveClient drive) {
        this.users = users;
        this.tokenCipher = tokenCipher;
        this.drive = drive;
    }

    @Override
    public String save(String userId, String key, byte[] content, String mimeType) {
        String accessToken = accessTokenFor(userId);
        String folderId = ensureIchiFolder(userId, accessToken);
        return drive.upload(accessToken, key, content, mimeType, folderId);
    }

    /**
     * 지정된 폴더에 파일 저장 (entryId 폴더, media 폴더 등).
     * folderId 는 호출자가 전달 (GoogleDriveClient로 생성한 폴더 ID).
     */
    public String saveToFolder(String userId, String filename, byte[] content, String mimeType, String folderId) {
        String accessToken = accessTokenFor(userId);
        return drive.upload(accessToken, filename, content, mimeType, folderId);
    }

    /**
     * 폴더 안에서 같은 이름 파일이 있으면 내용 교체, 없으면 새로 업로드.
     * 사람이 읽는 사본(예: 날짜_일기장.txt)을 중복 없이 갱신할 때 쓴다.
     */
    public void saveOrReplaceInFolder(String userId, String filename, byte[] content, String mimeType, String folderId) {
        String accessToken = accessTokenFor(userId);
        String existingId = drive.findInFolder(accessToken, folderId, filename);
        if (existingId != null) {
            drive.update(accessToken, existingId, content, mimeType);
        } else {
            drive.upload(accessToken, filename, content, mimeType, folderId);
        }
    }

    @Override
    public void update(String userId, String fileId, byte[] content, String mimeType) {
        String accessToken = accessTokenFor(userId);
        ensureIchiFolder(userId, accessToken);  // 폴더 확인 (부작용: 생성)
        drive.update(accessToken, fileId, content, mimeType);
    }

    @Override
    public byte[] load(String userId, String fileId) {
        String accessToken = accessTokenFor(userId);
        ensureIchiFolder(userId, accessToken);  // 폴더 확인
        return drive.download(accessToken, fileId);
    }

    @Override
    public void delete(String userId, String fileId) {
        String accessToken = accessTokenFor(userId);
        ensureIchiFolder(userId, accessToken);  // 폴더 확인
        drive.delete(accessToken, fileId);
    }

    /**
     * Drive 연결 상태 + 저장소 용량(설정 화면용).
     * 연결 안 됨(토큰 없음)/오류여도 예외 없이 connected=false 로 반환한다.
     */
    public StorageStatus status(String userId) {
        User user = users.findById(userId).orElse(null);
        String refreshToken = user != null ? tokenCipher.decrypt(user.getRefreshToken()) : null;
        if (refreshToken == null || refreshToken.isBlank()) {
            return new StorageStatus(false, null, null);
        }
        try {
            var about = drive.about(drive.accessToken(refreshToken));
            var quota = about.path("storageQuota");
            Long limit = quota.path("limit").isMissingNode() ? null : quota.path("limit").asLong();
            Long usage = quota.path("usage").isMissingNode() ? null : quota.path("usage").asLong();
            return new StorageStatus(true, usage, limit);
        } catch (Exception e) {
            // 연결은 되어 있으나 용량 조회 실패
            return new StorageStatus(true, null, null);
        }
    }

    /** Drive 연결 여부 + 사용량/총량(byte). 미연결이면 usage/limit null. */
    public record StorageStatus(boolean connected, Long usageBytes, Long limitBytes) {
    }

    /** accessToken을 공개 메서드로 (EntryService가 폴더 생성 시 필요). */
    public String getAccessTokenForDrive(String userId) {
        return accessTokenFor(userId);
    }

    /** ichi 폴더 ID를 확인하고 필요하면 생성 (공개 메서드). */
    public String getOrCreateIchiFolder(String userId) {
        String accessToken = accessTokenFor(userId);
        return ensureIchiFolder(userId, accessToken);
    }

    /** 사용자의 (암호화된) refresh_token 을 복호화해 액세스 토큰으로 교환. */
    private String accessTokenFor(String userId) {
        User user = users.findById(userId)
            .orElseThrow(() -> new DriveException("사용자를 찾을 수 없습니다: " + userId));
        String refreshToken = tokenCipher.decrypt(user.getRefreshToken());
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new DriveException("Drive 접근 권한(refresh_token)이 없습니다. 다시 로그인해 주세요.");
        }
        return drive.accessToken(refreshToken);
    }

    /**
     * "ichi" 폴더 ID 확인 및 자동 생성.
     * ichiFolder 가 null 이면 Drive 에서 생성 → DB 에 저장 → 반환.
     * 이미 존재하면 그대로 반환.
     */
    private String ensureIchiFolder(String userId, String accessToken) {
        User user = users.findById(userId)
            .orElseThrow(() -> new DriveException("사용자를 찾을 수 없습니다: " + userId));

        // 이미 폴더가 설정되어 있으면 그대로 반환
        if (user.getIchiFolder() != null && !user.getIchiFolder().isBlank()) {
            return user.getIchiFolder();
        }

        // 없으면 새로 생성
        String folderId = drive.createIchiFolder(accessToken);
        user.updateIchiFolder(folderId);
        users.save(user);

        return folderId;
    }
}
