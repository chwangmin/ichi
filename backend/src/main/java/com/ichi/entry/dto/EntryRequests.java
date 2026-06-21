package com.ichi.entry.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/** 노트 작성/수정 요청 DTO 모음. */
public final class EntryRequests {

    private EntryRequests() {
    }

    /** 작성: 본문 HTML + (선택) 날짜/색/위치/연결할 미디어 id 들. */
    public record Create(
        @NotNull String html,
        LocalDate entryDate,
        String color,
        Double lat,
        Double lng,
        String placeName,
        List<UUID> mediaIds
    ) {
    }

    /** 본문/위치/새 미디어 연결 수정. */
    public record UpdateContent(
        @NotNull String html,
        Double lat,
        Double lng,
        String placeName,
        List<UUID> mediaIds
    ) {
    }

    /** 색 변경. */
    public record ChangeColor(String color) {
    }
}
