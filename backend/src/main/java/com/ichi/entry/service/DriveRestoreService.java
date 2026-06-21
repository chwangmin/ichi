package com.ichi.entry.service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ichi.entry.domain.Entry;
import com.ichi.entry.repository.EntryRepository;
import com.ichi.storage.drive.GoogleDriveClient;
import com.ichi.storage.drive.GoogleDriveClient.DriveFile;
import com.ichi.storage.service.GoogleDriveStorage;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Drive 복원(re-index).
 *
 * 본문의 원본은 Drive(ichi/ 폴더 트리)이고 DB(entries)는 포인터일 뿐이다.
 * DB 가 비거나(재설치·기기 이전·메타 유실) 사용자가 돌아왔을 때, Drive 를 스캔해
 * <b>DB 에 없는(=누락된) 일기만</b> 메타 행으로 다시 INSERT 한다. 기존 행은 건드리지 않는다.
 *
 * 폴더 구조: ichi/YYYY-MM-DD/&lt;preview10&gt;_&lt;entryId&gt;/json/body.json
 *  - entry_date  : 날짜 폴더명
 *  - id(entryId) : 일기 폴더명 끝 UUID
 *  - drive_file_id: body.json 파일 ID
 *  - preview/위치 : body.json 내용 (version 3 부터 위치 포함)
 *
 * 한계: media 행(사진/영상)은 재구성하지 않는다. (docs/DEVELOPMENT.md "Drive 복원" 참고)
 */
@Service
public class DriveRestoreService {

    private static final int PREVIEW_MAX = 200;

    private final EntryRepository entries;
    private final GoogleDriveStorage storage;
    private final GoogleDriveClient drive;
    private final HtmlSanitizer sanitizer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DriveRestoreService(EntryRepository entries, GoogleDriveStorage storage,
            GoogleDriveClient drive, HtmlSanitizer sanitizer) {
        this.entries = entries;
        this.storage = storage;
        this.drive = drive;
        this.sanitizer = sanitizer;
    }

    /** 복원 결과 요약. scanned=Drive 에서 발견한 일기 폴더 수, restored=새로 넣은 수, skipped=이미 있던 수. */
    public record RestoreResult(int scanned, int restored, int skipped) {
    }

    /**
     * Drive 를 스캔해 누락 일기를 복원한다.
     *
     * @param apply false 면 실제 INSERT 없이 미리보기(몇 건이 복원 대상인지)만 계산한다.
     */
    @Transactional
    public RestoreResult restore(String userId, boolean apply) {
        String accessToken = storage.getAccessTokenForDrive(userId);
        String ichiFolder = storage.getOrCreateIchiFolder(userId);

        Set<UUID> existing = new HashSet<>(entries.findIdsByUserId(userId));

        int scanned = 0;
        int restored = 0;
        int skipped = 0;

        // ichi/ → 날짜 폴더들
        for (DriveFile dateFolder : drive.listChildren(accessToken, ichiFolder)) {
            if (!dateFolder.isFolder()) {
                continue;
            }
            LocalDate date = parseDate(dateFolder.name());
            if (date == null) {
                continue; // ichi 직속의 다른 폴더는 무시
            }

            // 날짜 폴더 → 일기 폴더들 (<preview10>_<entryId>)
            for (DriveFile entryFolder : drive.listChildren(accessToken, dateFolder.id())) {
                if (!entryFolder.isFolder()) {
                    continue;
                }
                UUID entryId = parseEntryId(entryFolder.name());
                if (entryId == null) {
                    continue; // 이름에 UUID 가 없으면 일기 폴더가 아님
                }
                scanned++;

                if (existing.contains(entryId)) {
                    skipped++;
                    continue;
                }

                if (apply) {
                    boolean ok = restoreOne(accessToken, userId, entryId, date, entryFolder.id());
                    if (ok) {
                        restored++;
                        existing.add(entryId); // 같은 ID 가 또 나와도 중복 INSERT 방지
                    } else {
                        skipped++; // body.json 없거나 손상 → 건너뜀
                    }
                } else {
                    restored++; // 미리보기: 복원 대상 카운트만
                }
            }
        }

        return new RestoreResult(scanned, restored, skipped);
    }

    /** 일기 폴더 하나를 읽어 메타 행으로 INSERT. body.json 을 못 읽으면 false. */
    private boolean restoreOne(String accessToken, String userId, UUID entryId,
            LocalDate date, String entryFolderId) {
        // 일기 폴더 → json 폴더 → body.json
        String jsonFolderId = childFolderId(accessToken, entryFolderId, "json");
        if (jsonFolderId == null) {
            return false;
        }
        DriveFile bodyFile = childByName(accessToken, jsonFolderId, "body.json");
        if (bodyFile == null) {
            return false;
        }

        JsonNode body;
        try {
            byte[] bytes = drive.download(accessToken, bodyFile.id());
            body = objectMapper.readTree(new String(bytes, StandardCharsets.UTF_8));
        } catch (Exception e) {
            return false; // 손상/비어있음
        }

        String html = body.path("html").asString("");
        String preview = sanitizer.toPreview(html, PREVIEW_MAX);

        // 위치: version 3 이상이면 body.json 에 들어있음. 구버전(없음)은 null.
        Double lat = body.hasNonNull("lat") ? body.path("lat").asDouble() : null;
        Double lng = body.hasNonNull("lng") ? body.path("lng").asDouble() : null;
        String placeName = body.hasNonNull("placeName") ? body.path("placeName").asString() : null;

        Entry entry = Entry.builder()
            .id(entryId)
            .userId(userId)
            .driveFileId(bodyFile.id())
            .entryFolderId(entryFolderId)
            .entryDate(date)
            .preview(preview)
            .pinned(false)
            .color(null) // 색은 Drive 에 없음 → 기본값
            .lat((lat != null && lng != null) ? lat : null)
            .lng((lat != null && lng != null) ? lng : null)
            .placeName(placeName)
            .build();
        entries.save(entry);
        return true;
    }

    /** 폴더명이 "YYYY-MM-DD" 면 날짜로, 아니면 null. */
    private LocalDate parseDate(String name) {
        try {
            return LocalDate.parse(name);
        } catch (Exception e) {
            return null;
        }
    }

    /** "<preview10>_<entryId>" 폴더명 끝의 UUID 추출. 마지막 '_' 뒤를 UUID 로 파싱. */
    private UUID parseEntryId(String folderName) {
        int us = folderName.lastIndexOf('_');
        String tail = us >= 0 ? folderName.substring(us + 1) : folderName;
        try {
            return UUID.fromString(tail);
        } catch (Exception e) {
            return null;
        }
    }

    private String childFolderId(String accessToken, String parentId, String name) {
        DriveFile f = childByName(accessToken, parentId, name);
        return (f != null && f.isFolder()) ? f.id() : null;
    }

    private DriveFile childByName(String accessToken, String parentId, String name) {
        List<DriveFile> children = drive.listChildren(accessToken, parentId);
        return children.stream().filter(c -> name.equals(c.name())).findFirst().orElse(null);
    }
}
