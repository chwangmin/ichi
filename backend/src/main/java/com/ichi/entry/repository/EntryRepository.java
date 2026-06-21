package com.ichi.entry.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.ichi.entry.domain.Entry;

public interface EntryRepository extends JpaRepository<Entry, UUID> {

    /** 본인 일기를 날짜 내림차순(같은 날짜는 생성 내림차순)으로. */
    List<Entry> findByUserIdOrderByEntryDateDescCreatedAtDesc(String userId);

    /** 캘린더: 기간 내 본인 일기 (날짜 내림차순). from·to 포함. */
    List<Entry> findByUserIdAndEntryDateBetweenOrderByEntryDateDescCreatedAtDesc(
        String userId, LocalDate from, LocalDate to);

    /** 아틀라스: 위치(lat·lng)가 있는 본인 일기. */
    List<Entry> findByUserIdAndLatIsNotNullAndLngIsNotNull(String userId);

    /** 설정: 내 일기 수. */
    long countByUserId(String userId);

    /** 복원: 본인이 이미 가진 일기 ID 집합 (Drive 스캔 결과와 비교용). */
    @Query("select e.id from Entry e where e.userId = :userId")
    List<UUID> findIdsByUserId(String userId);
}
