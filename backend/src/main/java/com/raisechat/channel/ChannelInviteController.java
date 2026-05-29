package com.raisechat.channel;

import com.raisechat.auth.jwt.AuthenticatedUser;
import com.raisechat.channel.dto.ChannelResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

// チャンネル招待受諾のエンドポイント。token をパスに含むため /api/channels/{id} 配下ではなく独立した URL を持つ。
// ワークスペース招待の /api/invites/{token}/accept と衝突しないよう /api/channel-invites を用いる。
// 例外を ProblemDetail で返すため com.raisechat.channel パッケージ配下に置く
// （GlobalExceptionHandler の basePackages ホワイトリスト対象）。
@RestController
public class ChannelInviteController {

    private final ChannelService channelService;

    public ChannelInviteController(ChannelService channelService) {
        this.channelService = channelService;
    }

    // 招待を受諾し、呼び出しユーザーをチャンネルに参加させる。参加したチャンネルを返す（200）。
    @PostMapping("/api/channel-invites/{token}/accept")
    public ChannelResponse accept(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable String token
    ) {
        return channelService.acceptInvite(principal.id(), token);
    }
}
