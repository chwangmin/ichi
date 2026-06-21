package com.ichi.entry.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ichi.entry.domain.Media;
import com.ichi.entry.exception.EntryAccessException;
import com.ichi.entry.repository.MediaRepository;
import com.ichi.storage.service.StorageService;

/**
 * 미디어 업로드/조회.
 * M3: 본문 인라인 이미지. M4: 갤러리 목록 + 썸네일 + 영상.
 */
@Service
public class MediaService {

    private final MediaRepository media;
    private final StorageService storage;
    private final Thumbnailer thumbnailer;

    public MediaService(MediaRepository media, StorageService storage, Thumbnailer thumbnailer) {
        this.media = media;
        this.storage = storage;
        this.thumbnailer = thumbnailer;
    }

    /** 이미지 업로드 → Drive 원본 + 썸네일 저장 → media 행. entry_id 는 작성 완료 시 연결. */
    @Transactional
    public Media uploadImage(String userId, byte[] content, String mimeType) {
        UUID id = UUID.randomUUID();
        String driveFileId = storage.save(userId, "media-" + id, content, mimeType);

        // 갤러리 그리드용 썸네일 (실패해도 원본만으로 동작)
        String thumbFileId = null;
        byte[] thumb = thumbnailer.generate(content);
        if (thumb != null) {
            thumbFileId = storage.save(userId, "thumb-" + id + ".jpg", thumb, Thumbnailer.THUMB_MIME);
        }

        Media m = Media.builder()
            .id(id)
            .entryId(null)
            .userId(userId)
            .driveFileId(driveFileId)
            .thumbFileId(thumbFileId)
            .type("image")
            .build();
        return media.save(m);
    }

    /** 영상 업로드 → Drive 저장 → media 행. 썸네일은 없음(프론트가 video 포스터 처리). */
    @Transactional
    public Media uploadVideo(String userId, byte[] content, String mimeType) {
        UUID id = UUID.randomUUID();
        String driveFileId = storage.save(userId, "media-" + id, content, mimeType);
        Media m = Media.builder()
            .id(id)
            .entryId(null)
            .userId(userId)
            .driveFileId(driveFileId)
            .type("video")
            .build();
        return media.save(m);
    }

    /** 갤러리: 본인 미디어 전체(최신순). */
    @Transactional(readOnly = true)
    public List<Media> gallery(String userId) {
        return media.findByUserIdOrderByCreatedAtDesc(userId);
    }

    /** 원본 바이트 (인라인 표시/영상 재생). */
    @Transactional(readOnly = true)
    public byte[] loadOwned(String userId, UUID mediaId) {
        Media m = requireOwned(userId, mediaId);
        return storage.load(userId, m.getDriveFileId());
    }

    /** 썸네일 바이트. 썸네일이 없으면 원본으로 폴백. */
    @Transactional(readOnly = true)
    public byte[] loadThumb(String userId, UUID mediaId) {
        Media m = requireOwned(userId, mediaId);
        String fileId = m.getThumbFileId() != null ? m.getThumbFileId() : m.getDriveFileId();
        return storage.load(userId, fileId);
    }

    private Media requireOwned(String userId, UUID mediaId) {
        Media m = media.findById(mediaId).orElseThrow(EntryAccessException::notFound);
        if (!m.ownedBy(userId)) {
            throw EntryAccessException.forbidden();
        }
        return m;
    }
}
