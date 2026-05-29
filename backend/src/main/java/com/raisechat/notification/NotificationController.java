package com.raisechat.notification;

import com.raisechat.auth.jwt.AuthenticatedUser;
import com.raisechat.notification.dto.MarkReadRequest;
import com.raisechat.notification.dto.UnreadResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/api/notifications/unread")
    public UnreadResponse getUnread(@AuthenticationPrincipal AuthenticatedUser principal) {
        return notificationService.getUnread(principal.id());
    }

    @PostMapping("/api/channels/{id}/read")
    public ResponseEntity<Void> markChannelRead(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long id,
            @RequestBody(required = false) MarkReadRequest req
    ) {
        notificationService.markChannelRead(principal.id(), id, req);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/dm/rooms/{id}/read")
    public ResponseEntity<Void> markDmRead(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long id,
            @RequestBody(required = false) MarkReadRequest req
    ) {
        notificationService.markDmRead(principal.id(), id, req);
        return ResponseEntity.noContent().build();
    }
}
