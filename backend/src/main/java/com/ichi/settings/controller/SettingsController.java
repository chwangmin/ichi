package com.ichi.settings.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ichi.entry.repository.EntryRepository;
import com.ichi.storage.service.GoogleDriveStorage;

/**
 * 설정 (§7) — Drive 저장소 상태.
 *  GET /api/settings/storage  연결 여부 + 사용량 + 내 일기 수
 */
@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private final GoogleDriveStorage drive;
    private final EntryRepository entries;

    public SettingsController(GoogleDriveStorage drive, EntryRepository entries) {
        this.drive = drive;
        this.entries = entries;
    }

    @GetMapping("/storage")
    public StorageStatusResponse storage(@AuthenticationPrincipal String userId) {
        GoogleDriveStorage.StorageStatus status = drive.status(userId);
        long entryCount = entries.countByUserId(userId);
        return new StorageStatusResponse(
            status.connected(), status.usageBytes(), status.limitBytes(), entryCount);
    }

    public record StorageStatusResponse(
        boolean connected,
        Long usageBytes,
        Long limitBytes,
        long entryCount
    ) {
    }
}
