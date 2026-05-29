package com.raisechat.message;

import com.raisechat.auth.jwt.AuthenticatedUser;
import com.raisechat.message.dto.AddReactionRequest;
import com.raisechat.message.dto.ReactionResponse;
import com.raisechat.message.dto.ReactionResult;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReactionController {

    private final MessageService messageService;

    public ReactionController(MessageService messageService) {
        this.messageService = messageService;
    }

    @PostMapping("/api/messages/{id}/reactions")
    public ResponseEntity<ReactionResponse> addReaction(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long id,
            @Valid @RequestBody AddReactionRequest req
    ) {
        ReactionResult result = messageService.addReaction(principal.id(), id, req);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result.reaction());
    }

    @DeleteMapping("/api/messages/{id}/reactions/{emoji}")
    public ResponseEntity<Void> removeReaction(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long id,
            @PathVariable String emoji
    ) {
        messageService.removeReaction(principal.id(), id, emoji);
        return ResponseEntity.noContent().build();
    }
}
