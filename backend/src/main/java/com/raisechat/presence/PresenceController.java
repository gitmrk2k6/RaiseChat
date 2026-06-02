package com.raisechat.presence;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * presence の seed エンドポイント。クライアントは WS 接続直後にこれを呼び、現在オンラインな
 * ユーザー一覧で初期状態を作る。以降の変化は {@code /topic/presence} の WS 受信で更新する。
 */
@RestController
public class PresenceController {

    private final PresenceService presenceService;

    public PresenceController(PresenceService presenceService) {
        this.presenceService = presenceService;
    }

    @GetMapping("/api/presence")
    public PresenceResponse getOnline() {
        return new PresenceResponse(presenceService.onlineUserIds());
    }
}
