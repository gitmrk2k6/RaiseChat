package com.raisechat.channel;

import com.raisechat.auth.jwt.AuthenticatedUser;
import com.raisechat.channel.dto.ChannelInviteResponse;
import com.raisechat.channel.dto.ChannelListResponse;
import com.raisechat.channel.dto.ChannelResponse;
import com.raisechat.channel.dto.CreateChannelRequest;
import com.raisechat.workspace.dto.CreateInviteRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// パスが /api/workspaces/{wsId}/channels と /api/channels/{id} の 2 系統あるため
// クラスレベル @RequestMapping は付けず、各メソッドで full path を指定する
@RestController
public class ChannelController {

    private final ChannelService channelService;

    public ChannelController(ChannelService channelService) {
        this.channelService = channelService;
    }

    @PostMapping("/api/workspaces/{wsId}/channels")
    @ResponseStatus(HttpStatus.CREATED)
    public ChannelResponse create(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long wsId,
            @Valid @RequestBody CreateChannelRequest req
    ) {
        return channelService.create(principal.id(), wsId, req);
    }

    @GetMapping("/api/workspaces/{wsId}/channels")
    public ChannelListResponse list(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long wsId,
            @RequestParam(required = false) ChannelType type,
            @RequestParam(required = false) Boolean joined,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit
    ) {
        return channelService.list(principal.id(), wsId, type, joined, cursor, limit);
    }

    @GetMapping("/api/channels/{id}")
    public ChannelResponse getDetail(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long id
    ) {
        return channelService.getDetail(principal.id(), id);
    }

    @PostMapping("/api/channels/{id}/join")
    public ChannelResponse join(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long id
    ) {
        return channelService.join(principal.id(), id);
    }

    @PostMapping("/api/channels/{id}/leave")
    public ResponseEntity<Void> leave(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long id
    ) {
        channelService.leave(principal.id(), id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/channels/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long id
    ) {
        channelService.delete(principal.id(), id);
        return ResponseEntity.noContent().build();
    }

    // 招待リンクを発行する（チャンネルメンバーのみ）。ボディは任意（{} で既定値）。
    @PostMapping("/api/channels/{id}/invites")
    @ResponseStatus(HttpStatus.CREATED)
    public ChannelInviteResponse createInvite(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long id,
            @Valid @RequestBody(required = false) CreateInviteRequest req
    ) {
        CreateInviteRequest body = req == null ? new CreateInviteRequest(null, null) : req;
        return channelService.createInvite(principal.id(), id, body);
    }

    // 招待リンクを無効化する（チャンネルメンバーのみ）。
    @DeleteMapping("/api/channels/{id}/invites/{inviteId}")
    public ResponseEntity<Void> revokeInvite(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long id,
            @PathVariable Long inviteId
    ) {
        channelService.revokeInvite(principal.id(), id, inviteId);
        return ResponseEntity.noContent().build();
    }
}
