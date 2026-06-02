package com.raisechat.presence;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * presence の変化イベントを Redis pub/sub（{@code presence:changed}）から受け、
 * STOMP {@code /topic/presence} へ中継する。
 *
 * <p>presence はユーザー単位グローバルなので topic は 1 本。発行インスタンスと購読インスタンスが
 * 異なっても届くよう Redis を経由する（メッセージ配信と同じ冗長化対応）。
 */
@Component
public class PresenceRedisSubscriber implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(PresenceRedisSubscriber.class);

    private static final String DESTINATION = "/topic/presence";

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    public PresenceRedisSubscriber(SimpMessagingTemplate messagingTemplate, ObjectMapper objectMapper) {
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            PresenceEvent event = objectMapper.readValue(message.getBody(), PresenceEvent.class);
            messagingTemplate.convertAndSend(DESTINATION, event);
        } catch (Exception e) {
            log.error("Presence Redis subscriber failed to process message: {}", e.getMessage(), e);
        }
    }
}
