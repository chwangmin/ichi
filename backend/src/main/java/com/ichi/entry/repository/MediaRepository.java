package com.ichi.entry.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ichi.entry.domain.Media;

public interface MediaRepository extends JpaRepository<Media, UUID> {

    List<Media> findByEntryId(UUID entryId);

    List<Media> findByUserIdOrderByCreatedAtDesc(String userId);

    /** 핀 썸네일용: 한 일기의 첫 이미지(가장 먼저 올린 것). */
    Media findFirstByEntryIdAndTypeOrderByCreatedAtAsc(UUID entryId, String type);
}
