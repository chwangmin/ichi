package com.ichi.entry.service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.ichi.entry.domain.Entry;
import com.ichi.entry.domain.Media;
import com.ichi.entry.dto.AtlasPin;
import com.ichi.entry.exception.EntryAccessException;
import com.ichi.entry.repository.EntryRepository;
import com.ichi.entry.repository.MediaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ichi.entry.dto.EntryResponse;
import com.ichi.entry.dto.EntryRequests;
import com.ichi.storage.service.GoogleDriveStorage;
import com.ichi.storage.drive.GoogleDriveClient;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 노트 도메인 서비스.
 *
 * 작성: 본문 HTML 을 sanitize → Drive 본문 JSON 저장(StorageService) → entries 메타 INSERT.
 * 본문은 DB 에 들어가지 않는다. preview(텍스트 발췌)만 검색/카드용으로 둔다.
 */
@Service
public class EntryService {

    private static final String BODY_MIME = "application/json";
    private static final int PREVIEW_MAX = 200;

    private final EntryRepository entries;
    private final MediaRepository media;
    private final GoogleDriveStorage storage;
    private final HtmlSanitizer sanitizer;
    private final GoogleDriveClient drive;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public EntryService(EntryRepository entries, MediaRepository media,
            GoogleDriveStorage storage, HtmlSanitizer sanitizer, GoogleDriveClient drive) {
        this.entries = entries;
        this.media = media;
        this.storage = storage;
        this.sanitizer = sanitizer;
        this.drive = drive;
    }

    @Transactional(readOnly = true)
    public List<Entry> list(String userId) {
        return entries.findByUserIdOrderByEntryDateDescCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public EntryResponse toResponse(Entry entry) {
        Media first = media.findFirstByEntryIdAndTypeOrderByCreatedAtAsc(entry.getId(), "image");
        return EntryResponse.from(entry, first != null ? first.getId() : null);
    }

    /** 캘린더용: 기간(from~to 포함) 내 일기. */
    @Transactional(readOnly = true)
    public List<Entry> listBetween(String userId, LocalDate from, LocalDate to) {
        return entries.findByUserIdAndEntryDateBetweenOrderByEntryDateDescCreatedAtDesc(userId, from, to);
    }

    /** 아틀라스용: 위치 있는 일기 + 핀 썸네일(첫 이미지). */
    @Transactional(readOnly = true)
    public List<AtlasPin> atlasPins(String userId) {
        return entries.findByUserIdAndLatIsNotNullAndLngIsNotNull(userId).stream()
            .map(e -> {
                Media first = media.findFirstByEntryIdAndTypeOrderByCreatedAtAsc(e.getId(), "image");
                return new AtlasPin(
                    e.getId(), e.getLat(), e.getLng(), e.getPlaceName(),
                    e.getPreview(), e.getColor(),
                    first != null ? first.getId() : null);
            })
            .toList();
    }

    /** 상세: 메타 + Drive 본문 HTML. */
    @Transactional(readOnly = true)
    public LoadedEntry load(String userId, UUID id) {
        Entry entry = requireOwned(userId, id);
        byte[] body = storage.load(userId, entry.getDriveFileId());
        String html = readHtml(body);
        return new LoadedEntry(entry, html);
    }

    @Transactional
    public Entry create(String userId, EntryRequests.Create req) {
        String safeHtml = sanitizer.sanitize(req.html());
        String preview = sanitizer.toPreview(safeHtml, PREVIEW_MAX);
        LocalDate date = req.entryDate() != null ? req.entryDate() : LocalDate.now();
        UUID id = UUID.randomUUID();

        // Drive 폴더 구조 생성: ichi/YYYY-MM-DD/<preview10글자>_<entryId>/
        String accessToken = storage.getAccessTokenForDrive(userId);
        String ichiFolder = storage.getOrCreateIchiFolder(userId);
        String dateFolderId = drive.createDateFolder(accessToken, ichiFolder, date);
        String entryFolderId = drive.createEntryFolder(accessToken, dateFolderId, id, preview);
        String jsonFolderId = drive.createJsonFolder(accessToken, entryFolderId);
        String mediaFolderId = drive.createMediaFolder(accessToken, entryFolderId);

        // 앱이 읽고 쓰는 원본 본문 JSON 을 json/ 폴더에 저장 (driveFileId 가 이 파일을 가리킨다)
        byte[] body = buildBody(safeHtml, req.lat(), req.lng(), req.placeName());
        String driveFileId = storage.saveToFolder(userId, "body.json", body, BODY_MIME, jsonFolderId);

        // 사람이 Drive 에서 바로 읽는 사본(태그 제거 텍스트)을 entryId 폴더에 저장
        byte[] bodyText = buildBodyText(safeHtml);
        storage.saveOrReplaceInFolder(userId, txtName(date), bodyText, "text/plain", entryFolderId);

        Entry entry = Entry.builder()
            .id(id)
            .userId(userId)
            .driveFileId(driveFileId)
            .entryFolderId(entryFolderId)
            .entryDate(date)
            .preview(preview)
            .pinned(false)
            .color(req.color())
            .lat(req.lat())
            .lng(req.lng())
            .placeName(req.placeName())
            .build();
        entries.save(entry);

        // 작성 중 업로드해 둔 미디어를 이 일기에 연결
        attachMedia(userId, id, req.mediaIds(), mediaFolderId);
        return entry;
    }

    @Transactional
    public Entry updateContent(String userId, UUID id, EntryRequests.UpdateContent req) {
        Entry entry = requireOwned(userId, id);
        String safeHtml = sanitizer.sanitize(req.html());
        // 앱이 읽는 원본 JSON 갱신 (위치도 body.json 에 함께 반영)
        storage.update(userId, entry.getDriveFileId(),
            buildBody(safeHtml, req.lat(), req.lng(), req.placeName()), BODY_MIME);
        // 사람이 읽는 사본(날짜_일기장.txt)도 함께 갱신 (entryId 폴더 안에서 이름으로 찾아 교체)
        if (entry.getEntryFolderId() != null && !entry.getEntryFolderId().isBlank()) {
            storage.saveOrReplaceInFolder(userId, txtName(entry.getEntryDate()),
                buildBodyText(safeHtml), "text/plain", entry.getEntryFolderId());
        }
        entry.updateContent(sanitizer.toPreview(safeHtml, PREVIEW_MAX), req.lat(), req.lng(), req.placeName());
        attachMedia(userId, id, req.mediaIds(), entry.getEntryFolderId());
        return entry;
    }

    @Transactional
    public Entry togglePin(String userId, UUID id) {
        Entry entry = requireOwned(userId, id);
        entry.togglePin();
        return entry;
    }

    @Transactional
    public Entry changeColor(String userId, UUID id, String color) {
        Entry entry = requireOwned(userId, id);
        entry.changeColor(color);
        return entry;
    }

    @Transactional
    public void delete(String userId, UUID id) {
        Entry entry = requireOwned(userId, id);
        
        // entryFolderId 폴더 전체 삭제 (body.json + media/ 포함)
        if (entry.getEntryFolderId() != null && !entry.getEntryFolderId().isBlank()) {
            safeDeleteDrive(userId, entry.getEntryFolderId());
        }
        
        // DB에서 미디어 행 삭제 (Drive는 폴더 삭제로 이미 정리됨)
        for (Media m : media.findByEntryId(id)) {
            media.delete(m);
        }
        
        entries.delete(entry);
    }

    // --- 내부 ---

    private Entry requireOwned(String userId, UUID id) {
        Entry entry = entries.findById(id).orElseThrow(EntryAccessException::notFound);
        if (!entry.ownedBy(userId)) {
            throw EntryAccessException.forbidden();
        }
        return entry;
    }

    private void attachMedia(String userId, UUID entryId, List<UUID> mediaIds, String mediaFolderId) {
        if (mediaIds == null) {
            return;
        }
        for (UUID mediaId : mediaIds) {
            media.findById(mediaId)
                .filter(m -> m.ownedBy(userId))
                .ifPresent(m -> m.attachTo(entryId));
        }
        // mediaFolderId는 향후 Drive 파일 이동용으로 사용 (M7+)
    }

    /**
     * 앱이 읽고 쓰는 원본 본문 JSON (body.json).
     *
     * version 3 부터 위치(lat/lng/placeName)도 함께 적는다. 위치는 그동안 DB(entries)에만
     * 있었는데, body.json 이 본문의 원본인 만큼 Drive 만으로도 복원(re-index)할 때 위치가
     * 빠지지 않도록 포함한다. 위치가 없으면 키를 생략한다.
     * (참고: docs/DEVELOPMENT.md "Drive 복원 (re-index)")
     */
    private byte[] buildBody(String safeHtml, Double lat, Double lng, String placeName) {
        String now = OffsetDateTime.now().toString();
        ObjectNode node = objectMapper.createObjectNode();
        node.put("version", 3);
        node.put("html", safeHtml);
        if (lat != null && lng != null) {
            node.put("lat", lat);
            node.put("lng", lng);
        }
        if (placeName != null && !placeName.isBlank()) {
            node.put("placeName", placeName);
        }
        node.put("createdAt", now);
        node.put("updatedAt", now);
        return objectMapper.writeValueAsBytes(node);
    }

    /**
     * 사용자가 Google Drive에서 바로 읽을 수 있도록 태그를 제거한 순수 텍스트로 변환.
     * "YYYY-MM-DD_일기장.txt" 파일로 저장된다.
     */
    private byte[] buildBodyText(String safeHtml) {
        return sanitizer.toPlainText(safeHtml).getBytes(StandardCharsets.UTF_8);
    }

    /** 사람이 읽는 사본 파일명: "YYYY-MM-DD_일기장.txt". */
    private String txtName(LocalDate date) {
        return date + "_일기장.txt";
    }

    private String readHtml(byte[] body) {
        try {
            var node = objectMapper.readTree(new String(body, StandardCharsets.UTF_8));
            return node.path("html").asString("");
        } catch (Exception e) {
            // 손상/구버전 본문은 빈 HTML 로
            return "";
        }
    }

    private void safeDeleteDrive(String userId, String fileId) {
        try {
            storage.delete(userId, fileId);
        } catch (Exception ignored) {
            // Drive 정리 실패는 메타 삭제를 막지 않는다 (고아 파일은 추후 정리)
        }
    }

    /** 메타 + 본문 HTML. */
    public record LoadedEntry(Entry entry, String html) {
    }
}
