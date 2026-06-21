package com.ichi.entry.controller;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.ichi.entry.domain.Media;
import com.ichi.entry.service.MediaService;

/**
 * 미디어.
 *  POST /api/media           이미지 업로드 (인라인 + 썸네일)
 *  POST /api/media/video     영상 업로드
 *  GET  /api/media           갤러리 목록 (본인, 최신순)
 *  GET  /api/media/{id}/raw  원본 바이트 (인라인 표시/영상 재생)
 *  GET  /api/media/{id}/thumb 썸네일 바이트 (갤러리 그리드)
 */
@RestController
@RequestMapping("/api/media")
public class MediaController {

    private final MediaService mediaService;

    public MediaController(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    @PostMapping
    public MediaItem upload(
            @AuthenticationPrincipal String userId,
            @RequestParam("file") MultipartFile file) throws IOException {
        requireType(file, "image/");
        return MediaItem.from(mediaService.uploadImage(userId, file.getBytes(), file.getContentType()));
    }

    @PostMapping("/video")
    public MediaItem uploadVideo(
            @AuthenticationPrincipal String userId,
            @RequestParam("file") MultipartFile file) throws IOException {
        requireType(file, "video/");
        return MediaItem.from(mediaService.uploadVideo(userId, file.getBytes(), file.getContentType()));
    }

    @GetMapping
    public List<MediaItem> gallery(@AuthenticationPrincipal String userId) {
        return mediaService.gallery(userId).stream().map(MediaItem::from).toList();
    }

    @GetMapping("/{id}/raw")
    public ResponseEntity<byte[]> raw(
            @AuthenticationPrincipal String userId,
            @PathVariable UUID id) {
        return image(mediaService.loadOwned(userId, id));
    }

    @GetMapping("/{id}/thumb")
    public ResponseEntity<byte[]> thumb(
            @AuthenticationPrincipal String userId,
            @PathVariable UUID id) {
        return image(mediaService.loadThumb(userId, id));
    }

    private ResponseEntity<byte[]> image(byte[] bytes) {
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_OCTET_STREAM) // 브라우저가 실제 타입 추론
            .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
            .body(bytes);
    }

    private void requireType(MultipartFile file, String prefix) {
        String ct = file.getContentType();
        if (ct == null || !ct.startsWith(prefix)) {
            throw new IllegalArgumentException(
                prefix.startsWith("image") ? "이미지 파일만 업로드할 수 있습니다." : "영상 파일만 업로드할 수 있습니다.");
        }
    }

    /** 갤러리/업로드 응답. entryId 가 있으면 그 일기로 이동 가능. */
    public record MediaItem(UUID id, String type, UUID entryId) {
        static MediaItem from(Media m) {
            return new MediaItem(m.getId(), m.getType(), m.getEntryId());
        }
    }
}
