package com.ichi.entry.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

class EntryExceptionHandlerTest {

    @Test
    void maxUploadSizeExceededReturnsPayloadTooLargeMessage() {
        EntryExceptionHandler handler = new EntryExceptionHandler();

        ResponseEntity<Map<String, String>> response =
            handler.handleMaxUploadSize(new MaxUploadSizeExceededException(25 * 1024 * 1024L));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(response.getBody()).containsEntry("message", "파일은 25MB 이하로 업로드해 주세요.");
    }
}
