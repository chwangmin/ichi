package com.ichi.entry.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 미디어 메타데이터 (§4 media). 바이너리는 Drive.
 * M3 에서는 본문 인라인 이미지 업로드 시 행을 만든다. 썸네일(thumbFileId)·영상은 M4.
 */
@Entity
@Table(name = "media")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Media {

    @Id
    private UUID id;

    /** 어떤 일기에 속하는지. 본문 작성 중(아직 entry 없음)에 올린 경우 null 일 수 있음. */
    @Column(name = "entry_id")
    private UUID entryId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "drive_file_id", nullable = false)
    private String driveFileId;

    @Column(name = "thumb_file_id")
    private String thumbFileId;

    /** image / video */
    private String type;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Builder
    public Media(UUID id, UUID entryId, String userId, String driveFileId, String thumbFileId, String type) {
        this.id = id;
        this.entryId = entryId;
        this.userId = userId;
        this.driveFileId = driveFileId;
        this.thumbFileId = thumbFileId;
        this.type = type;
    }

    /** 작성 완료 시 일기에 연결. */
    public void attachTo(UUID entryId) {
        this.entryId = entryId;
    }

    public boolean ownedBy(String googleSub) {
        return this.userId.equals(googleSub);
    }
}
