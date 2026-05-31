package com.raisechat.user.exception;

/**
 * アバター画像が対応外の MIME タイプだった場合（→ 415 Unsupported Media Type）。
 */
public class UnsupportedAvatarTypeException extends RuntimeException {
    public UnsupportedAvatarTypeException(String contentType) {
        super("対応していない画像形式です: " + contentType + "（JPEG / PNG / GIF のみ）");
    }
}
