package com.raisechat.message;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raisechat.auth.jwt.AuthenticatedUser;
import com.raisechat.message.dto.TypingEvent;
import com.raisechat.user.User;
import com.raisechat.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * 入力中（typing）インジケータの送信エンドポイント。
 *
 * <p>typing は短命・高頻度・揮発なため永続化しない。受け取った typing を Redis pub/sub へ
 * そのまま fan-out するだけで、サーバ側には状態を持たない（消えるのはクライアント側タイマー）。
 * クライアントは throttle して送るため、displayName 解決のための軽量な参照だけ行う。
 */
@Controller
public class TypingWebSocketController {

    private static final Logger log = LoggerFactory.getLogger(TypingWebSocketController.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;

    @Value("${app.redis.typing-channel-prefix:typing:channel:}")
    private String typingChannelPrefix;

    @Value("${app.redis.typing-dm-prefix:typing:dm:}")
    private String typingDmPrefix;

    public TypingWebSocketController(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            UserRepository userRepository
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
    }

    @MessageMapping("/channels/{channelId}/typing")
    public void typingInChannel(@DestinationVariable Long channelId, Principal principal) {
        publishTyping(typingChannelPrefix + channelId, principal);
    }

    @MessageMapping("/dm/{roomId}/typing")
    public void typingInDm(@DestinationVariable Long roomId, Principal principal) {
        publishTyping(typingDmPrefix + roomId, principal);
    }

    private void publishTyping(String topic, Principal principal) {
        AuthenticatedUser user =
                (AuthenticatedUser) ((UsernamePasswordAuthenticationToken) principal).getPrincipal();
        String displayName = userRepository.findById(user.id())
                .map(User::getDisplayName)
                .orElse(user.userId());
        try {
            String json = objectMapper.writeValueAsString(new TypingEvent(user.id(), displayName));
            redisTemplate.convertAndSend(topic, json);
        } catch (JsonProcessingException e) {
            log.error("Typing publish failed for topic={}: {}", topic, e.getMessage(), e);
        }
    }
}
