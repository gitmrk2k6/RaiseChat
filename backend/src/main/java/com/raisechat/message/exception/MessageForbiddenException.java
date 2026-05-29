package com.raisechat.message.exception;

public class MessageForbiddenException extends RuntimeException {
    public MessageForbiddenException(String message) {
        super(message);
    }
}
