package com.raisechat.message.exception;

/**
 * 添付ファイルが対応外の MIME タイプだった場合（→ 415 Unsupported Media Type）。
 */
public class UnsupportedAttachmentTypeException extends RuntimeException {
    public UnsupportedAttachmentTypeException(String contentType) {
        super("対応していないファイル形式です: " + contentType
                + "（JPEG / PNG / GIF / WebP / MP4 のみ）");
    }
}
