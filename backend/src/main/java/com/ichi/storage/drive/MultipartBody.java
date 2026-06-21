package com.ichi.storage.drive;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import tools.jackson.databind.ObjectMapper;

/**
 * Google Drive multipart/related 업로드 본문 빌더.
 * part1 = JSON 메타데이터, part2 = 파일 바이트.
 */
final class MultipartBody {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String boundary;
    private final byte[] bytes;

    private MultipartBody(String boundary, byte[] bytes) {
        this.boundary = boundary;
        this.bytes = bytes;
    }

    String boundary() {
        return boundary;
    }

    byte[] bytes() {
        return bytes;
    }

    static MultipartBody related(Map<String, Object> metadata, byte[] content, String mimeType) {
        String boundary = "ichi_" + Long.toHexString(System.nanoTime());
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            String meta = "--" + boundary + "\r\n"
                + "Content-Type: application/json; charset=UTF-8\r\n\r\n"
                + MAPPER.writeValueAsString(metadata) + "\r\n";
            out.write(meta.getBytes(StandardCharsets.UTF_8));

            String partHeader = "--" + boundary + "\r\n"
                + "Content-Type: " + mimeType + "\r\n\r\n";
            out.write(partHeader.getBytes(StandardCharsets.UTF_8));
            out.write(content);
            out.write(("\r\n--" + boundary + "--").getBytes(StandardCharsets.UTF_8));

            return new MultipartBody(boundary, out.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
