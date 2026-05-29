package com.raisechat.dm.exception;

public class DmNotFoundException extends RuntimeException {
    public DmNotFoundException(String message) {
        super(message);
    }

    public static DmNotFoundException room(Long roomId) {
        return new DmNotFoundException("dm room not found: id=" + roomId);
    }

    public static DmNotFoundException partner(Long userId) {
        return new DmNotFoundException("partner user not found: id=" + userId);
    }
}
