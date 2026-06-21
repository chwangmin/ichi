package com.ichi.entry.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.ichi.entry.domain.Entry;
import com.ichi.entry.dto.AtlasPin;
import com.ichi.entry.service.DriveRestoreService;
import com.ichi.entry.service.EntryService;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ichi.entry.dto.EntryDetailResponse;
import com.ichi.entry.dto.EntryRequests;
import com.ichi.entry.dto.EntryResponse;

import jakarta.validation.Valid;

/**
 * 노트 CRUD (§7).
 *  POST   /api/entries           작성 (본문 HTML → Drive, 메타 INSERT)
 *  GET    /api/entries           목록 (날짜 내림차순, preview)
 *  GET    /api/entries/{id}      상세 (본문 HTML 포함)
 *  PATCH  /api/entries/{id}      본문 수정
 *  POST   /api/entries/{id}/pin  핀 토글
 *  PATCH  /api/entries/{id}/color 색 변경
 *  DELETE /api/entries/{id}      삭제
 *  GET    /api/entries/restore   복원 미리보기 (Drive 스캔, INSERT 안 함)
 *  POST   /api/entries/restore   복원 실행 (Drive 에만 있는 일기를 메타로 INSERT)
 */
@RestController
@RequestMapping("/api/entries")
public class EntryController {

    private final EntryService entryService;
    private final DriveRestoreService restoreService;

    public EntryController(EntryService entryService, DriveRestoreService restoreService) {
        this.entryService = entryService;
        this.restoreService = restoreService;
    }

    @PostMapping
    public ResponseEntity<EntryResponse> create(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody EntryRequests.Create req) {
        Entry entry = entryService.create(userId, req);
        return ResponseEntity.ok(entryService.toResponse(entry));
    }

    @GetMapping
    public List<EntryResponse> list(
            @AuthenticationPrincipal String userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        List<Entry> result = (from != null && to != null)
            ? entryService.listBetween(userId, from, to)
            : entryService.list(userId);
        return result.stream().map(entryService::toResponse).toList();
    }

    @GetMapping("/atlas")
    public List<AtlasPin> atlas(@AuthenticationPrincipal String userId) {
        return entryService.atlasPins(userId);
    }

    /** 복원 미리보기: Drive 를 스캔해 복원 대상이 몇 건인지만 계산 (INSERT 하지 않음). */
    @GetMapping("/restore")
    public DriveRestoreService.RestoreResult restorePreview(@AuthenticationPrincipal String userId) {
        return restoreService.restore(userId, false);
    }

    /** 복원 실행: Drive 에만 있는(=DB 누락) 일기를 메타 행으로 INSERT. */
    @PostMapping("/restore")
    public DriveRestoreService.RestoreResult restore(@AuthenticationPrincipal String userId) {
        return restoreService.restore(userId, true);
    }

    @GetMapping("/{id}")
    public EntryDetailResponse get(
            @AuthenticationPrincipal String userId,
            @PathVariable UUID id) {
        EntryService.LoadedEntry loaded = entryService.load(userId, id);
        return EntryDetailResponse.from(loaded.entry(), loaded.html());
    }

    @PatchMapping("/{id}")
    public EntryResponse update(
            @AuthenticationPrincipal String userId,
            @PathVariable UUID id,
            @Valid @RequestBody EntryRequests.UpdateContent req) {
        return entryService.toResponse(entryService.updateContent(userId, id, req));
    }

    @PostMapping("/{id}/pin")
    public EntryResponse togglePin(
            @AuthenticationPrincipal String userId,
            @PathVariable UUID id) {
        return entryService.toResponse(entryService.togglePin(userId, id));
    }

    @PatchMapping("/{id}/color")
    public EntryResponse changeColor(
            @AuthenticationPrincipal String userId,
            @PathVariable UUID id,
            @RequestBody EntryRequests.ChangeColor req) {
        return entryService.toResponse(entryService.changeColor(userId, id, req.color()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal String userId,
            @PathVariable UUID id) {
        entryService.delete(userId, id);
        return ResponseEntity.noContent().build();
    }
}
