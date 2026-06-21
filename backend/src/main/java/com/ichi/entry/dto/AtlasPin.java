package com.ichi.entry.dto;

import java.util.UUID;

/**
 * 아틀라스 핀 — 위치 있는 일기 1건.
 * thumbMediaId 가 있으면 지도 핀을 그 사진 썸네일로 그린다(없으면 기본 핀).
 */
public record AtlasPin(
    UUID id,
    double lat,
    double lng,
    String placeName,
    String preview,
    String color,
    UUID thumbMediaId
) {
}
