package com.raisechat.message.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.raisechat.message.dto.TypingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * typing イベントを Redis pub/sub から受け、STOMP /topic へ中継する。
 *
 * <p>topic 形式は {@code typing:channel:{id}} / {@code typing:dm:{id}} の 1 パターン（typing:*）で
 * 受け、scopeType（channel/dm）と id を切り出して {@code /topic/channels/{id}/typing} または
 * {@code /topic/dm/{id}/typing} へ配信する。発行インスタンスと購読インスタンスが異なっても
 * 届くよう、メッセージ配信と同じく Redis を経由する（冗長化対応）。永続化はしない。
 */
@Component
public class TypingRedisSubscriber implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(TypingRedisSubscriber.class);

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    public TypingRedisSubscriber(SimpMessagingTemplate messagingTemplate, ObjectMapper objectMapper) {
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            // topic 例: "typing:channel:5" → ["typing", "channel", "5"]
            String topic = new String(message.getChannel());
            String[] parts = topic.split(":");
            if (parts.length != 3) {
                log.warn("Unexpected typing topic: {}", topic);
                return;
            }
            String scopeType = parts[1]; // "channel" | "dm"
            String scopeId = parts[2];
            String destination = "channel".equals(scopeType)
                    ? "/topic/channels/" + scopeId + "/typing"
                    : "/topic/dm/" + scopeId + "/typing";
            TypingEvent event = objectMapper.readValue(message.getBody(), TypingEvent.class);
            messagingTemplate.convertAndSend(destination, event);
        } catch (Exception e) {
            log.error("Typing Redis subscriber failed to process message: {}", e.getMessage(), e);
        }
    }
}
