package com.ichi.entry.dto;

import java.time.LocalDate;
import java.util.UUID;

import com.ichi.entry.domain.Entry;

/** 카드 목록용 응답 (본문 HTML 미포함 — 목록은 preview 만). */
public record EntryResponse(
    UUID id,
    LocalDate entryDate,
    String preview,
    boolean pinned,
    String color,
    Double lat,
    Double lng,
    String placeName,
    UUID thumbMediaId
) {
    public static EntryResponse from(Entry e) {
        return from(e, null);
    }

    public static EntryResponse from(Entry e, UUID thumbMediaId) {
        return new EntryResponse(
            e.getId(), e.getEntryDate(), e.getPreview(), e.isPinned(),
            e.getColor(), e.getLat(), e.getLng(), e.getPlaceName(), thumbMediaId);
    }
}
