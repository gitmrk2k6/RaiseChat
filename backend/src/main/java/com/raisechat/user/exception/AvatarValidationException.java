package com.raisechat.user.exception;

/**
 * アバター画像のリクエストが不正な場合（ファイル未指定・空ファイル等 → 422）。
 */
public class AvatarValidationException extends RuntimeException {
    public AvatarValidationException(String message) {
        super(message);
    }
}
