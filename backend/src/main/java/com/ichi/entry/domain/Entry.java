package com.ichi.entry.domain;

import java.time.LocalDate;
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
 * 일기 메타데이터 (§4 entries). 본문은 DB에 없다 — driveFileId 가 가리키는 Drive JSON 에 있다.
 * 상태 변경은 setter 가 아니라 도메인 메서드로만.
 */
@Entity
@Table(name = "entries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Entry {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "drive_file_id", nullable = false)
    private String driveFileId;

    /** Drive 상의 entryId 폴더 ID (ichi/YYYY-MM-DD/entryId_preview/). */
    @Column(name = "entry_folder_id")
    private String entryFolderId;

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    /** 카드/검색용 텍스트 발췌 (HTML 에서 텍스트만 추출). 향후 E2E 시 암호화/제거 대상. */
    private String preview;

    private boolean pinned;

    /** 카드 색 (y/g/b/p/null). */
    private String color;

    private Double lat;
    private Double lng;

    @Column(name = "place_name")
    private String placeName;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Builder
    public Entry(UUID id, String userId, String driveFileId, String entryFolderId, LocalDate entryDate,
            String preview, boolean pinned, String color, Double lat, Double lng, String placeName) {
        this.id = id;
        this.userId = userId;
        this.driveFileId = driveFileId;
        this.entryFolderId = entryFolderId;
        this.entryDate = entryDate;
        this.preview = preview;
        this.pinned = pinned;
        this.color = color;
        this.lat = lat;
        this.lng = lng;
        this.placeName = placeName;
        this.updatedAt = OffsetDateTime.now();
    }

    /** 본문 수정에 따른 메타 갱신. */
    public void updateContent(String preview) {
        this.preview = preview;
        this.updatedAt = OffsetDateTime.now();
    }

    /** 본문 수정과 함께 위치 메타 갱신. */
    public void updateContent(String preview, Double lat, Double lng, String placeName) {
        this.preview = preview;
        this.lat = lat;
        this.lng = lng;
        this.placeName = placeName;
        this.updatedAt = OffsetDateTime.now();
    }

    public void togglePin() {
        this.pinned = !this.pinned;
        this.updatedAt = OffsetDateTime.now();
    }

    public void changeColor(String color) {
        this.color = color;
        this.updatedAt = OffsetDateTime.now();
    }

    /** Drive 상의 entryId 폴더 ID 저장. */
    public void updateEntryFolderId(String folderId) {
        this.entryFolderId = folderId;
    }

    /** 본인 소유인지 확인. */
    public boolean ownedBy(String googleSub) {
        return this.userId.equals(googleSub);
    }
}
