package com.raisechat.user.exception;

/**
 * アバター画像がサイズ上限（2MB）を超えた場合（→ 413 Payload Too Large）。
 */
public class AvatarTooLargeException extends RuntimeException {
    public AvatarTooLargeException(long maxBytes) {
        super("アバター画像が大きすぎます（上限 " + (maxBytes / (1024 * 1024)) + "MB）");
    }
}
