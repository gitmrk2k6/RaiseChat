package com.raisechat.message;

import com.raisechat.message.dto.AttachmentResponse;
import com.raisechat.message.exception.AttachmentTooLargeException;
import com.raisechat.message.exception.AttachmentValidationException;
import com.raisechat.message.exception.MessageForbiddenException;
import com.raisechat.message.exception.MessageNotFoundException;
import com.raisechat.message.exception.UnsupportedAttachmentTypeException;
import com.raisechat.storage.ObjectStorage;
import com.raisechat.storage.StorageProperties;
import com.raisechat.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AttachmentService {

    // 添付ファイルの上限サイズ（10MB）と許可 MIME → 拡張子の対応。
    // DB の attachments_mime_check / attachments_size_check と一致させる。
    private static final long MAX_ATTACHMENT_BYTES = 10L * 1024 * 1024;
    private static final Map<String, String> ALLOWED_CONTENT_TYPES = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/gif", "gif",
            "image/webp", "webp",
            "video/mp4", "mp4"
    );

    private final AttachmentRepository attachmentRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final ObjectStorage objectStorage;
    private final StorageProperties storageProperties;

    public AttachmentService(
            AttachmentRepository attachmentRepository,
            MessageRepository messageRepository,
            UserRepository userRepository,
            ObjectStorage objectStorage,
            StorageProperties storageProperties
    ) {
        this.attachmentRepository = attachmentRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.objectStorage = objectStorage;
        this.storageProperties = storageProperties;
    }

    @Transactional
    public AttachmentResponse upload(Long userId, Long messageId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AttachmentValidationException("添付ファイルが指定されていません");
        }

        String contentType = file.getContentType();
        String extension = ALLOWED_CONTENT_TYPES.get(contentType);
        if (extension == null) {
            throw new UnsupportedAttachmentTypeException(contentType);
        }

        if (file.getSize() > MAX_ATTACHMENT_BYTES) {
            throw new AttachmentTooLargeException(MAX_ATTACHMENT_BYTES);
        }

        Message message = messageRepository.findById(messageId)
                .filter(m -> m.getDeletedAt() == null)
                .orElseThrow(() -> new MessageNotFoundException(messageId));

        // 添付できるのは投稿者本人のみ（メッセージ編集と同じ権限モデル）。
        if (!message.getAuthor().getId().equals(userId)) {
            throw new MessageForbiddenException(
                    "添付の追加は投稿者のみ可能です: messageId=" + messageId);
        }

        String bucket = storageProperties.bucket();
        String key = "attachments/%d/%s.%s".formatted(messageId, UUID.randomUUID(), extension);

        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("添付ファイルの読み込みに失敗しました", e);
        }

        String url = objectStorage.upload(key, data, contentType);

        Attachment attachment = new Attachment();
        attachment.setMessage(message);
        attachment.setUploader(userRepository.getReferenceById(userId));
        attachment.setS3Bucket(bucket);
        attachment.setS3Key(key);
        attachment.setMimeType(contentType);
        attachment.setSizeBytes(file.getSize());
        attachment.setOriginalFilename(resolveFilename(file));
        attachmentRepository.saveAndFlush(attachment);

        return AttachmentResponse.from(attachment, url);
    }

    // メッセージ一覧の添付を 1 クエリでまとめてロードし、messageId ごとにグルーピングする。
    @Transactional(readOnly = true)
    public Map<Long, List<AttachmentResponse>> findByMessageIds(Collection<Long> messageIds) {
        if (messageIds.isEmpty()) {
            return Map.of();
        }
        return attachmentRepository.findByMessageIdInAndDeletedAtIsNullOrderByIdAsc(messageIds).stream()
                .collect(Collectors.groupingBy(
                        a -> a.getMessage().getId(),
                        Collectors.mapping(this::toResponse, Collectors.toList())));
    }

    @Transactional(readOnly = true)
    public List<AttachmentResponse> findByMessageId(Long messageId) {
        return attachmentRepository.findByMessageIdAndDeletedAtIsNullOrderByIdAsc(messageId).stream()
                .map(this::toResponse)
                .toList();
    }

    private AttachmentResponse toResponse(Attachment a) {
        return AttachmentResponse.from(a, objectStorage.resolveUrl(a.getS3Key()));
    }

    // ファイル名が取れない場合のフォールバック（DB は NOT NULL）。
    private String resolveFilename(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (name == null || name.isBlank()) {
            return "file";
        }
        return name.length() > 255 ? name.substring(name.length() - 255) : name;
    }
}
