package com.raisechat.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raisechat.notification.dto.NotificationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 未読更新イベントを Redis pub/sub（notifications:{userId}）へ発行する。
 *
 * <p>冗長化を見据え、メッセージ配信と同じく Redis を経由する。発行インスタンスと
 * 受信ユーザーの WebSocket セッションを持つインスタンスが異なっても届くようにするため、
 * 各インスタンスで購読する {@link com.raisechat.notification.ws.NotificationRedisSubscriber} が
 * 自分の持つセッションにだけ配信する。
 */
@Component
public class NotificationPublisher {

    private static final Logger log = LoggerFactory.getLogger(NotificationPublisher.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    @Value("${app.redis.notification-prefix:notifications:}")
    private String notificationPrefix;

    public NotificationPublisher(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public void publish(Long userId, NotificationEvent event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            redis.convertAndSend(notificationPrefix + userId, json);
        } catch (JsonProcessingException e) {
            log.error("Notification publish failed for userId={}: {}", userId, e.getMessage(), e);
        }
    }
}
