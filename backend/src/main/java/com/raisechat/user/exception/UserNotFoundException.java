package com.raisechat.user.exception;

public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(Long userId) {
        super("user not found: id=" + userId);
    }
}
