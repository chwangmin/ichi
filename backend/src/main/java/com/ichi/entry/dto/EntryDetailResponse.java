package com.ichi.entry.dto;

import java.time.LocalDate;
import java.util.UUID;

import com.ichi.entry.domain.Entry;

/** 상세 응답 — 본문 HTML 포함 (Drive 에서 로드). 인라인 <img data-ref> 는 프론트가 해석. */
public record EntryDetailResponse(
    UUID id,
    LocalDate entryDate,
    String html,
    String preview,
    boolean pinned,
    String color,
    Double lat,
    Double lng,
    String placeName
) {
    public static EntryDetailResponse from(Entry e, String html) {
        return new EntryDetailResponse(
            e.getId(), e.getEntryDate(), html, e.getPreview(), e.isPinned(),
            e.getColor(), e.getLat(), e.getLng(), e.getPlaceName());
    }
}
