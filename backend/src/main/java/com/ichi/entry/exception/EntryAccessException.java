package com.ichi.entry.exception;

/** 일기 접근 오류 (없음/권한 없음). */
public class EntryAccessException extends RuntimeException {

    private final boolean notFound;

    private EntryAccessException(String message, boolean notFound) {
        super(message);
        this.notFound = notFound;
    }

    public static EntryAccessException notFound() {
        return new EntryAccessException("일기를 찾을 수 없습니다.", true);
    }

    public static EntryAccessException forbidden() {
        return new EntryAccessException("이 일기에 접근할 수 없습니다.", false);
    }

    public boolean isNotFound() {
        return notFound;
    }
}
