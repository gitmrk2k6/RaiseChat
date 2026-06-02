package com.raisechat.presence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;

/**
 * STOMP のセッション接続/切断イベントを拾って {@link PresenceService} に橋渡しする。
 *
 * <p>CONNECT 時に {@code JwtChannelInterceptor} が principal をセット済みなので、イベントの
 * {@code getUser()} から認証ユーザーを取れる。{@code AuthenticatedUser#getName()} は数値 id を返すため
 * そのまま userId として使う。セッションIDは参照カウントの要素キー。
 */
@Component
public class PresenceEventListener {

    private static final Logger log = LoggerFactory.getLogger(PresenceEventListener.class);

    private final PresenceService presenceService;

    public PresenceEventListener(PresenceService presenceService) {
        this.presenceService = presenceService;
    }

    @EventListener
    public void onConnected(SessionConnectedEvent event) {
        Long userId = userIdOf(event.getUser());
        String sessionId = StompHeaderAccessor.wrap(event.getMessage()).getSessionId();
        if (userId == null || sessionId == null) return;
        presenceService.connected(userId, sessionId);
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        Long userId = userIdOf(event.getUser());
        String sessionId = event.getSessionId();
        if (userId == null || sessionId == null) return;
        presenceService.disconnected(userId, sessionId);
    }

    /** principal の getName()（=数値 id）を Long に変換する。未認証や想定外形式なら null。 */
    private Long userIdOf(Principal principal) {
        if (principal == null) return null;
        try {
            return Long.valueOf(principal.getName());
        } catch (NumberFormatException e) {
            log.warn("Unexpected presence principal name: {}", principal.getName());
            return null;
        }
    }
}
