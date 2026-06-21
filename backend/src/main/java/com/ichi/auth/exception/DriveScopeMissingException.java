package com.ichi.auth.exception;

/**
 * 로그인 시 Google Drive(drive.file) 권한이 부여되지 않은 경우.
 * 이치는 본문을 사용자 Drive 에 저장하므로 이 권한이 필수다.
 */
public class DriveScopeMissingException extends RuntimeException {

    public DriveScopeMissingException() {
        super("이치를 쓰려면 Google Drive 접근 권한이 필요합니다. "
            + "로그인할 때 Google Drive 권한(파일 보기·관리)을 허용해 주세요.");
    }
}
