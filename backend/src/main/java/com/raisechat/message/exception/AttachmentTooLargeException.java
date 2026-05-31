package com.raisechat.message.exception;

/**
 * 添付ファイルがサイズ上限（10MB）を超えた場合（→ 413 Payload Too Large）。
 */
public class AttachmentTooLargeException extends RuntimeException {
    public AttachmentTooLargeException(long maxBytes) {
        super("添付ファイルが大きすぎます（上限 " + (maxBytes / (1024 * 1024)) + "MB）");
    }
}
