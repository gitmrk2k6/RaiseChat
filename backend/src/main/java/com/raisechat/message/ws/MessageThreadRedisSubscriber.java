package com.raisechat.message.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.raisechat.message.dto.WsEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class MessageThreadRedisSubscriber implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(MessageThreadRedisSubscriber.class);

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    public MessageThreadRedisSubscriber(SimpMessagingTemplate messagingTemplate, ObjectMapper objectMapper) {
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String topic = new String(message.getChannel());
            String parentId = topic.substring(topic.lastIndexOf(':') + 1);
            WsEvent event = objectMapper.readValue(message.getBody(), WsEvent.class);
            messagingTemplate.convertAndSend("/topic/threads/" + parentId, event);
        } catch (Exception e) {
            log.error("Redis thread subscriber failed to process message: {}", e.getMessage(), e);
        }
    }
}
