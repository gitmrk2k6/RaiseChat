package com.raisechat.message;

import com.raisechat.auth.jwt.AuthenticatedUser;
import com.raisechat.message.dto.EditMessageRequest;
import com.raisechat.message.dto.MessageListResponse;
import com.raisechat.message.dto.MessageResponse;
import com.raisechat.message.dto.ReplyMessageRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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
