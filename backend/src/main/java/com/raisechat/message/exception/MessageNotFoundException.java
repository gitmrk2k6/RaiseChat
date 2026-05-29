package com.raisechat.message.exception;

public class MessageNotFoundException extends RuntimeException {
    public MessageNotFoundException(Long messageId) {
        super("message not found: id=" + messageId);
    }
}
