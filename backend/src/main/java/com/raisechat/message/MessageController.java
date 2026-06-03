package com.raisechat.message;

import com.raisechat.auth.jwt.AuthenticatedUser;
import com.raisechat.message.dto.EditMessageRequest;
import com.raisechat.message.dto.MessageListResponse;
import com.raisechat.message.dto.MessageResponse;
import com.raisechat.message.dto.ReplyMessageRequest;
import com.raisechat.message.exception.AttachmentValidationException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    @GetMapping("/api/channels/{id}/messages")
    public MessageListResponse listMessages(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long id,
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false) Integer limit
    ) {
        return messageService.listChannelMessages(principal.id(), id, cursor, limit);
    }

    @GetMapping("/api/dm/rooms/{id}/messages")
    public MessageListResponse listDmMessages(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long id,
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false) Integer limit
    ) {
        return messageService.listDmMessages(principal.id(), id, cursor, limit);
    }

    // 添付メッセージで一度に受け付けるファイル数の上限。
    private static final int MAX_ATTACHMENTS = 10;

    // F-10: チャンネルへ本文＋ファイルを一括投稿する（multipart）。ファイル 1〜10 件必須。
    // 本文は任意（file-only 添付を許可。本文なしは空文字として保存する）。
    // 添付なしの通常メッセージは従来どおり WebSocket 経路で送るため、本エンドポイントは multipart のみ受け付ける。
    @PostMapping(value = "/api/channels/{id}/messages", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public MessageResponse createChannelMessageWithAttachments(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long id,
            @RequestParam(value = "body", required = false) String body,
            @RequestParam(value = "parentMessageId", required = false) Long parentMessageId,
            @RequestParam("files") List<MultipartFile> files
    ) {
        validateAttachmentMessage(body, files);
        return messageService.sendChannelMessageWithAttachments(principal.id(), id, normalizeBody(body), parentMessageId, files);
    }

    // F-10: DM ルームへ本文＋ファイルを一括投稿する（multipart、チャンネル版と同仕様）。
    @PostMapping(value = "/api/dm/rooms/{id}/messages", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public MessageResponse createDmMessageWithAttachments(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long id,
            @RequestParam(value = "body", required = false) String body,
            @RequestParam(value = "parentMessageId", required = false) Long parentMessageId,
            @RequestParam("files") List<MultipartFile> files
    ) {
        validateAttachmentMessage(body, files);
        return messageService.sendDmMessageWithAttachments(principal.id(), id, normalizeBody(body), parentMessageId, files);
    }

    // 本文長（最大 4000 文字）とファイル件数（1〜MAX_ATTACHMENTS）を検証する。違反は 422。
    // 本文は任意: このエンドポイントは files 必須なので、本文が空でも「本文 or 添付」の要件は満たされる。
    private void validateAttachmentMessage(String body, List<MultipartFile> files) {
        if (body != null && body.trim().length() > 4000) {
            throw new AttachmentValidationException("本文は 4000 文字以内にしてください");
        }
        if (files == null || files.isEmpty()) {
            throw new AttachmentValidationException("添付ファイルが指定されていません");
        }
        if (files.size() > MAX_ATTACHMENTS) {
            throw new AttachmentValidationException("添付できるファイルは最大 " + MAX_ATTACHMENTS + " 件です");
        }
    }

    // null 本文（file-only）を空文字に正規化し、前後の空白を落とす。body は NOT NULL のため "" を保存する。
    private static String normalizeBody(String body) {
        return body == null ? "" : body.trim();
    }

    @GetMapping("/api/messages/{parentId}/replies")
    public MessageListResponse listReplies(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long parentId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false) Integer limit
    ) {
        return messageService.listReplies(principal.id(), parentId, cursor, limit);
    }

    @PostMapping("/api/messages/{parentId}/replies")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageResponse createReply(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long parentId,
            @Valid @RequestBody ReplyMessageRequest req
    ) {
        return messageService.createReply(principal.id(), parentId, req);
    }

    @PatchMapping("/api/messages/{id}")
    public MessageResponse editMessage(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long id,
            @Valid @RequestBody EditMessageRequest req
    ) {
        return messageService.editMessage(principal.id(), id, req);
    }

    @DeleteMapping("/api/messages/{id}")
    public ResponseEntity<Void> deleteMessage(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long id
    ) {
        messageService.deleteMessage(principal.id(), id);
        return ResponseEntity.noContent().build();
    }
}
