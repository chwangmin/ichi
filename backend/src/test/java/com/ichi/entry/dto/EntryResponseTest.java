package com.ichi.entry.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.ichi.entry.domain.Entry;

class EntryResponseTest {

    @Test
    void includesFirstImageThumbnailIdForImageCards() {
        UUID entryId = UUID.randomUUID();
        UUID thumbMediaId = UUID.randomUUID();
        Entry entry = Entry.builder()
            .id(entryId)
            .userId("user")
            .driveFileId("drive-file")
            .entryDate(LocalDate.of(2026, 6, 17))
            .preview("")
            .pinned(false)
            .color("y")
            .build();

        EntryResponse response = EntryResponse.from(entry, thumbMediaId);

        assertThat(response.id()).isEqualTo(entryId);
        assertThat(response.preview()).isEmpty();
        assertThat(response.thumbMediaId()).isEqualTo(thumbMediaId);
    }
}
