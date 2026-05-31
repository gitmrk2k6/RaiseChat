package com.raisechat.message.dto;

import com.raisechat.message.Attachment;

import java.time.OffsetDateTime;

/**
 * メッセージ添付ファイル（F-10）のレスポンス。
 * S3 のキーは外部に晒さず、復元済みの公開 URL のみ返す。
 */
public record AttachmentResponse(
        Long id,
        Long messageId,
        Long uploaderId,
        String url,
        String mimeType,
        Long sizeBytes,
        String originalFilename,
        Integer width,
        Integer height,
        Integer durationSec,
        OffsetDateTime createdAt
) {
    public static AttachmentResponse from(Attachment a, String url) {
        return new AttachmentResponse(
                a.getId(),
                a.getMessage().getId(),
                a.getUploader().getId(),
                url,
                a.getMimeType(),
                a.getSizeBytes(),
                a.getOriginalFilename(),
                a.getWidth(),
                a.getHeight(),
                a.getDurationSec(),
                a.getCreatedAt()
        );
    }
}
