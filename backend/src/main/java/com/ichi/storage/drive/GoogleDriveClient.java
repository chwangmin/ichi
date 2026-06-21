package com.ichi.storage.drive;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import com.ichi.config.IchiProperties;

import tools.jackson.databind.JsonNode;

/**
 * Google Drive v3 REST 호출 래퍼 (drive.file 스코프, 일반 Drive 의 "ichi" 폴더 트리).
 * 앱이 만든 파일/폴더만 접근하므로 drive.file 권한이면 충분하다.
 * 무거운 google-api-services-drive 대신 RestClient 로 직접 호출한다.
 *
 * 액세스 토큰은 refresh_token 으로 갱신해서 받는다 (저장된 토큰은 호출자가 복호화해 전달).
 */
@Component
public class GoogleDriveClient {

    private static final String TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token";
    private static final String UPLOAD_ENDPOINT =
        "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id";
    private static final String FILES_ENDPOINT = "https://www.googleapis.com/drive/v3/files";

    private final IchiProperties props;
    private final RestClient rest;

    public GoogleDriveClient(IchiProperties props, RestClient.Builder builder) {
        this.props = props;
        this.rest = builder.build();
    }

    /** refresh_token(평문) 으로 액세스 토큰을 갱신해 반환. */
    public String accessToken(String refreshToken) {
        var google = props.getGoogle();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", google.getClientId());
        form.add("client_secret", google.getClientSecret());
        form.add("refresh_token", refreshToken);
        form.add("grant_type", "refresh_token");

        JsonNode res = rest.post()
            .uri(TOKEN_ENDPOINT)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(form)
            .retrieve()
            .body(JsonNode.class);

        if (res == null || res.path("access_token").isMissingNode()) {
            throw new DriveException("Drive 액세스 토큰 갱신에 실패했습니다.");
        }
        return res.path("access_token").asString();
    }

    /**
     * "ichi" 폴더에 파일 업로드 (multipart: 메타 + 바이트). 파일 ID 반환.
     */
    public String upload(String accessToken, String name, byte[] content, String mimeType, String folderId) {
        Map<String, Object> metadata = Map.of(
            "name", name,
            "parents", List.of(folderId)
        );

        // multipart/related 본문을 직접 구성
        MultipartBody body = MultipartBody.related(metadata, content, mimeType);

        JsonNode res = rest.post()
            .uri(UPLOAD_ENDPOINT)
            .header("Authorization", "Bearer " + accessToken)
            .contentType(MediaType.parseMediaType("multipart/related; boundary=" + body.boundary()))
            .body(body.bytes())
            .retrieve()
            .body(JsonNode.class);

        if (res == null || res.path("id").isMissingNode()) {
            throw new DriveException("Drive 업로드에 실패했습니다.");
        }
        return res.path("id").asString();
    }

    /**
     * 폴더 안에서 이름으로 파일 ID 검색 (없으면 null).
     * trashed 제외, 폴더 자체는 제외하지 않으므로 일반 파일 이름 조회용으로만 쓴다.
     */
    public String findInFolder(String accessToken, String folderId, String name) {
        String escaped = name.replace("\\", "\\\\").replace("'", "\\'");
        String query = "name='" + escaped + "' and trashed=false and '" + folderId + "' in parents";
        JsonNode listRes = rest.get()
            .uri("https://www.googleapis.com/drive/v3/files?q={query}&spaces=drive&fields=files(id)", query)
            .header("Authorization", "Bearer " + accessToken)
            .retrieve()
            .body(JsonNode.class);

        if (listRes != null && listRes.path("files").isArray() && listRes.path("files").size() > 0) {
            return listRes.path("files").get(0).path("id").asString();
        }
        return null;
    }

    /** 기존 파일 내용 교체 (수정). */
    public void update(String accessToken, String fileId, byte[] content, String mimeType) {
        rest.patch()
            .uri("https://www.googleapis.com/upload/drive/v3/files/{id}?uploadType=media", fileId)
            .header("Authorization", "Bearer " + accessToken)
            .contentType(MediaType.parseMediaType(mimeType))
            .body(content)
            .retrieve()
            .toBodilessEntity();
    }

    /** 파일 바이트 다운로드. */
    public byte[] download(String accessToken, String fileId) {
        byte[] bytes = rest.get()
            .uri(FILES_ENDPOINT + "/{id}?alt=media", fileId)
            .header("Authorization", "Bearer " + accessToken)
            .retrieve()
            .body(byte[].class);
        if (bytes == null) {
            throw new DriveException("Drive 파일을 불러오지 못했습니다: " + fileId);
        }
        return bytes;
    }

    /** 파일 삭제. */
    public void delete(String accessToken, String fileId) {
        rest.delete()
            .uri(FILES_ENDPOINT + "/{id}", fileId)
            .header("Authorization", "Bearer " + accessToken)
            .retrieve()
            .toBodilessEntity();
    }

    /** Drive about: 저장소 용량/사용량. fields=storageQuota. */
    public JsonNode about(String accessToken) {
        JsonNode res = rest.get()
            .uri("https://www.googleapis.com/drive/v3/about?fields=storageQuota")
            .header("Authorization", "Bearer " + accessToken)
            .retrieve()
            .body(JsonNode.class);
        if (res == null) {
            throw new DriveException("Drive 상태를 불러오지 못했습니다.");
        }
        return res;
    }

    /**
     * 폴더의 직속 자식들을 나열. (복원/스캔용)
     * 휴지통 제외, 페이지네이션으로 전부 모은다. 각 원소: id, name, mimeType.
     */
    public List<DriveFile> listChildren(String accessToken, String folderId) {
        String query = "'" + folderId + "' in parents and trashed=false";
        List<DriveFile> out = new java.util.ArrayList<>();
        String pageToken = null;
        do {
            String uri = FILES_ENDPOINT
                + "?q={query}&spaces=drive&pageSize=1000"
                + "&fields=nextPageToken,files(id,name,mimeType)"
                + (pageToken != null ? "&pageToken={pageToken}" : "");
            JsonNode res = (pageToken != null
                ? rest.get().uri(uri, query, pageToken)
                : rest.get().uri(uri, query))
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .body(JsonNode.class);

            if (res == null) {
                break;
            }
            for (JsonNode f : res.path("files")) {
                out.add(new DriveFile(
                    f.path("id").asString(""),
                    f.path("name").asString(""),
                    f.path("mimeType").asString("")));
            }
            JsonNode next = res.path("nextPageToken");
            pageToken = next.isMissingNode() ? null : next.asString("");
        } while (pageToken != null && !pageToken.isBlank());
        return out;
    }

    /** Drive 파일/폴더 한 항목 (복원 스캔용). */
    public record DriveFile(String id, String name, String mimeType) {
        public boolean isFolder() {
            return "application/vnd.google-apps.folder".equals(mimeType);
        }
    }

    /**
     * My Drive 루트에 "ichi" 폴더 생성 (또는 기존 폴더 재사용).
     * 중복 생성 방지를 위해 같은 이름의 폴더가 있으면 그 ID를 반환한다.
     */
    public String createIchiFolder(String accessToken) {
        // 1. 기존 "ichi" 폴더 검색
        String query = "name='ichi' and trashed=false and mimeType='application/vnd.google-apps.folder'";
        JsonNode listRes = rest.get()
            .uri("https://www.googleapis.com/drive/v3/files?q={query}&spaces=drive&fields=files(id)", query)
            .header("Authorization", "Bearer " + accessToken)
            .retrieve()
            .body(JsonNode.class);

        if (listRes != null && listRes.path("files").isArray() && listRes.path("files").size() > 0) {
            // 기존 폴더 있음 → 첫 번째 ID 반환
            return listRes.path("files").get(0).path("id").asString();
        }

        // 2. 없으면 새로 생성
        Map<String, Object> metadata = Map.of(
            "name", "ichi",
            "mimeType", "application/vnd.google-apps.folder"
        );

        JsonNode createRes = rest.post()
            .uri("https://www.googleapis.com/drive/v3/files?fields=id")
            .header("Authorization", "Bearer " + accessToken)
            .contentType(MediaType.APPLICATION_JSON)
            .body(metadata)
            .retrieve()
            .body(JsonNode.class);

        if (createRes == null || createRes.path("id").isMissingNode()) {
            throw new DriveException("Drive 'ichi' 폴더 생성에 실패했습니다.");
        }
        return createRes.path("id").asString();
    }

    /**
     * "ichi" 폴더 내에 "YYYY-MM-DD" 폴더 생성 또는 재사용.
     * parentFolderId: "ichi" 폴더 ID
     */
    public String createDateFolder(String accessToken, String parentFolderId, LocalDate date) {
        String folderName = date.toString(); // "2026-06-17"

        // 1. 기존 폴더 검색
        String query = "name='" + folderName + "' and trashed=false and mimeType='application/vnd.google-apps.folder' and '" + parentFolderId + "' in parents";
        JsonNode listRes = rest.get()
            .uri("https://www.googleapis.com/drive/v3/files?q={query}&spaces=drive&fields=files(id)", query)
            .header("Authorization", "Bearer " + accessToken)
            .retrieve()
            .body(JsonNode.class);

        if (listRes != null && listRes.path("files").isArray() && listRes.path("files").size() > 0) {
            return listRes.path("files").get(0).path("id").asString();
        }

        // 2. 새로 생성
        Map<String, Object> metadata = Map.of(
            "name", folderName,
            "mimeType", "application/vnd.google-apps.folder",
            "parents", List.of(parentFolderId)
        );

        JsonNode createRes = rest.post()
            .uri("https://www.googleapis.com/drive/v3/files?fields=id")
            .header("Authorization", "Bearer " + accessToken)
            .contentType(MediaType.APPLICATION_JSON)
            .body(metadata)
            .retrieve()
            .body(JsonNode.class);

        if (createRes == null || createRes.path("id").isMissingNode()) {
            throw new DriveException("Drive 날짜 폴더 생성에 실패했습니다: " + folderName);
        }
        return createRes.path("id").asString();
    }

    /**
     * 날짜 폴더 내에 entryId 폴더 생성.
     * 폴더명: "<preview_10글자>_<entryId>"
     * parentFolderId: 날짜 폴더(YYYY-MM-DD) ID
     */
    public String createEntryFolder(String accessToken, String parentFolderId, UUID entryId, String preview) {
        // preview 에서 처음 10글자 추출 (띄어쓰기/특수문자 포함)
        String previewShort = preview != null && preview.length() > 10
            ? preview.substring(0, 10).trim()
            : (preview != null ? preview.trim() : "");

        String folderName = previewShort + "_" + entryId;

        // entryId 폴더는 매번 새로 생성 (고유함)
        Map<String, Object> metadata = Map.of(
            "name", folderName,
            "mimeType", "application/vnd.google-apps.folder",
            "parents", List.of(parentFolderId)
        );

        JsonNode createRes = rest.post()
            .uri("https://www.googleapis.com/drive/v3/files?fields=id")
            .header("Authorization", "Bearer " + accessToken)
            .contentType(MediaType.APPLICATION_JSON)
            .body(metadata)
            .retrieve()
            .body(JsonNode.class);

        if (createRes == null || createRes.path("id").isMissingNode()) {
            throw new DriveException("Drive entryId 폴더 생성에 실패했습니다: " + folderName);
        }
        return createRes.path("id").asString();
    }

    /**
     * entryId 폴더 내에 "media" 폴더 생성 또는 재사용.
     * parentFolderId: entryId 폴더 ID
     */
    public String createMediaFolder(String accessToken, String parentFolderId) {
        String folderName = "media";

        // 1. 기존 폴더 검색
        String query = "name='media' and trashed=false and mimeType='application/vnd.google-apps.folder' and '" + parentFolderId + "' in parents";
        JsonNode listRes = rest.get()
            .uri("https://www.googleapis.com/drive/v3/files?q={query}&spaces=drive&fields=files(id)", query)
            .header("Authorization", "Bearer " + accessToken)
            .retrieve()
            .body(JsonNode.class);

        if (listRes != null && listRes.path("files").isArray() && listRes.path("files").size() > 0) {
            return listRes.path("files").get(0).path("id").asString();
        }

        // 2. 새로 생성
        Map<String, Object> metadata = Map.of(
            "name", folderName,
            "mimeType", "application/vnd.google-apps.folder",
            "parents", List.of(parentFolderId)
        );

        JsonNode createRes = rest.post()
            .uri("https://www.googleapis.com/drive/v3/files?fields=id")
            .header("Authorization", "Bearer " + accessToken)
            .contentType(MediaType.APPLICATION_JSON)
            .body(metadata)
            .retrieve()
            .body(JsonNode.class);

        if (createRes == null || createRes.path("id").isMissingNode()) {
            throw new DriveException("Drive media 폴더 생성에 실패했습니다.");
        }
        return createRes.path("id").asString();
    }

    /**
     * entryId 폴더 내에 "json" 폴더 생성 또는 재사용.
     * parentFolderId: entryId 폴더 ID
     */
    public String createJsonFolder(String accessToken, String parentFolderId) {
        String folderName = "json";

        // 1. 기존 폴더 검색
        String query = "name='json' and trashed=false and mimeType='application/vnd.google-apps.folder' and '" + parentFolderId + "' in parents";
        JsonNode listRes = rest.get()
            .uri("https://www.googleapis.com/drive/v3/files?q={query}&spaces=drive&fields=files(id)", query)
            .header("Authorization", "Bearer " + accessToken)
            .retrieve()
            .body(JsonNode.class);

        if (listRes != null && listRes.path("files").isArray() && listRes.path("files").size() > 0) {
            return listRes.path("files").get(0).path("id").asString();
        }

        // 2. 새로 생성
        Map<String, Object> metadata = Map.of(
            "name", folderName,
            "mimeType", "application/vnd.google-apps.folder",
            "parents", List.of(parentFolderId)
        );

        JsonNode createRes = rest.post()
            .uri("https://www.googleapis.com/drive/v3/files?fields=id")
            .header("Authorization", "Bearer " + accessToken)
            .contentType(MediaType.APPLICATION_JSON)
            .body(metadata)
            .retrieve()
            .body(JsonNode.class);

        if (createRes == null || createRes.path("id").isMissingNode()) {
            throw new DriveException("Drive json 폴더 생성에 실패했습니다.");
        }
        return createRes.path("id").asString();
    }
}

