package com.raisechat.message;

import com.raisechat.auth.jwt.AuthenticatedUser;
import com.raisechat.message.dto.EditMessageRequest;
import com.raisechat.message.dto.MessageListResponse;
import com.raisechat.message.dto.MessageResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
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
