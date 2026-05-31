package com.raisechat.message.exception;

/**
 * 添付ファイルのリクエストが不正な場合（ファイル未指定・空ファイル等 → 422）。
 */
public class AttachmentValidationException extends RuntimeException {
    public AttachmentValidationException(String message) {
        super(message);
    }
}
