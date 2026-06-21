package com.ichi.storage.drive;

/** Google Drive 호출 중 발생하는 오류. */
public class DriveException extends RuntimeException {

    public DriveException(String message) {
        super(message);
    }

    public DriveException(String message, Throwable cause) {
        super(message, cause);
    }
}
