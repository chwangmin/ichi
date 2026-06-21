package com.ichi.entry.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class EntryEditTest {

    @Test
    void updateContentCanAlsoUpdateLocationMetadata() {
        Entry entry = Entry.builder()
            .id(java.util.UUID.randomUUID())
            .userId("user")
            .driveFileId("drive-file")
            .entryDate(LocalDate.of(2026, 6, 17))
            .preview("before")
            .build();

        entry.updateContent("after", 37.5665, 126.9780, "서울");

        assertThat(entry.getPreview()).isEqualTo("after");
        assertThat(entry.getLat()).isEqualTo(37.5665);
        assertThat(entry.getLng()).isEqualTo(126.9780);
        assertThat(entry.getPlaceName()).isEqualTo("서울");
    }
}
