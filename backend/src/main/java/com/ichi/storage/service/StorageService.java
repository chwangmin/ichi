package com.ichi.storage.service;

/**
 * 저장소 추상화 — 일기 본문/미디어의 저장·조회·삭제는 반드시 이 인터페이스 한 곳을 통과한다.
 *
 * 이번 구현체: GoogleDriveStorage (사용자 Drive 의 "ichi" 폴더, drive.file 스코프). — M3에서 추가.
 *
 * 향후: EncryptedStorage(StorageService delegate) 로 감싸기만 하면
 *       content 를 암호화/복호화할 수 있다. 그때는 entries.preview 도
 *       암호화/제거 대상이 된다. (E2E 암호화는 이번 MVP 범위 밖)
 */
public interface StorageService {

    /** content 를 새로 저장하고 저장소의 파일 ID를 반환한다. */
    String save(String userId, String key, byte[] content, String mimeType);

    /** 기존 fileId 의 content 를 교체한다 (수정). */
    void update(String userId, String fileId, byte[] content, String mimeType);

    /** fileId 로 content 를 불러온다. */
    byte[] load(String userId, String fileId);

    /** fileId 의 파일을 삭제한다. */
    void delete(String userId, String fileId);
}
