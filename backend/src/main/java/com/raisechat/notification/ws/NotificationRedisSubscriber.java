package com.raisechat.notification.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.raisechat.notification.dto.NotificationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis の notifications:{userId} を購読し、該当ユーザーの WebSocket セッション
 * （/user/queue/notifications）へ未読更新イベントを転送する。
 *
 * <p>convertAndSendToUser は当該ユーザーのセッションがこのインスタンスに無ければ何もしないため、
 * 全インスタンスで購読していてもセッションを持つインスタンスだけが実際に配信する。
 */
@Component
public class NotificationRedisSubscriber implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationRedisSubscriber.class);

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    public NotificationRedisSubscriber(SimpMessagingTemplate messagingTemplate, ObjectMapper objectMapper) {
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String topic = new String(message.getChannel());
            String userId = topic.substring(topic.lastIndexOf(':') + 1);
            NotificationEvent event = objectMapper.readValue(message.getBody(), NotificationEvent.class);
            messagingTemplate.convertAndSendToUser(userId, "/queue/notifications", event);
        } catch (Exception e) {
            log.error("Notification subscriber failed to process message: {}", e.getMessage(), e);
        }
    }
}
