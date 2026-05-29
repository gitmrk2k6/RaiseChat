package com.raisechat.message;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raisechat.channel.Channel;
import com.raisechat.channel.ChannelMemberRepository;
import com.raisechat.channel.ChannelRepository;
import com.raisechat.message.dto.EditMessageRequest;
import com.raisechat.message.dto.MessageListResponse;
import com.raisechat.message.dto.MessageResponse;
import com.raisechat.message.dto.SendMessageRequest;
import com.raisechat.message.dto.WsEvent;
import com.raisechat.message.exception.MessageForbiddenException;
import com.raisechat.message.exception.MessageNotFoundException;
import com.raisechat.user.UserRepository;
import com.raisechat.workspace.WorkspaceMemberRepository;
import com.raisechat.workspace.WorkspaceRole;
import com.raisechat.workspace.exception.WorkspaceNotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class MessageService {

    private static final Logger log = LoggerFactory.getLogger(MessageService.class);
    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final MessageRepository messageRepository;
    private final ChannelRepository channelRepository;
    private final ChannelMemberRepository channelMemberRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final UserRepository userRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @PersistenceContext
    private EntityManager entityManager;

    @Value("${app.redis.channel-prefix:messages:channel:}")
    private String redisChannelPrefix;

    public MessageService(
            MessageRepository messageRepository,
            ChannelRepository channelRepository,
            ChannelMemberRepository channelMemberRepository,
            WorkspaceMemberRepository workspaceMemberRepository,
            UserRepository userRepository,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper
    ) {
        this.messageRepository = messageRepository;
        this.channelRepository = channelRepository;
        this.channelMemberRepository = channelMemberRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.userRepository = userRepository;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public MessageListResponse listChannelMessages(Long userId, Long channelId, Long cursorId, Integer limit) {
        channelRepository.findByIdAndDeletedAtIsNull(channelId)
                .orElseThrow(() -> new MessageNotFoundException(channelId));
        requireChannelMember(channelId, userId);

        int effectiveLimit = clampLimit(limit);
        long effectiveCursor = cursorId != null ? cursorId : 0L;

        List<Message> rows = messageRepository.findChannelMessagesBefore(
                channelId, effectiveCursor, PageRequest.ofSize(effectiveLimit + 1));

        boolean hasMore = rows.size() > effectiveLimit;
        List<Message> page = hasMore ? rows.subList(0, effectiveLimit) : rows;

        List<MessageResponse> items = page.stream().map(MessageResponse::from).toList();
        String nextCursor = hasMore ? String.valueOf(page.get(page.size() - 1).getId()) : null;

        return new MessageListResponse(items, nextCursor, hasMore);
    }

    @Transactional
    public MessageResponse sendChannelMessage(Long userId, Long channelId, SendMessageRequest req) {
        Channel channel = channelRepository.findByIdAndDeletedAtIsNull(channelId)
                .orElseThrow(() -> new MessageNotFoundException(channelId));
        requireChannelMember(channelId, userId);

        Message message = new Message();
        message.setWorkspace(channel.getWorkspace());
        message.setChannel(channel);
        message.setAuthor(userRepository.getReferenceById(userId));
        message.setBody(req.body());

        if (req.parentMessageId() != null) {
            Message parent = messageRepository.findById(req.parentMessageId())
                    .filter(p -> p.getDeletedAt() == null)
                    .orElseThrow(() -> new MessageNotFoundException(req.parentMessageId()));
            message.setParent(parent);
        }

        messageRepository.saveAndFlush(message);
        entityManager.refresh(message);

        MessageResponse dto = MessageResponse.from(message);
        publishToRedis(channelId, WsEvent.EventType.MESSAGE_CREATED, dto);
        return dto;
    }

    @Transactional
    public MessageResponse editMessage(Long userId, Long messageId, EditMessageRequest req) {
        Message message = messageRepository.findById(messageId)
                .filter(m -> m.getDeletedAt() == null)
                .orElseThrow(() -> new MessageNotFoundException(messageId));

        if (!message.getAuthor().getId().equals(userId)) {
            throw new MessageForbiddenException("メッセージの編集は投稿者のみ可能です: messageId=" + messageId);
        }

        message.setBody(req.body());
        message.setEditedAt(OffsetDateTime.now());
        messageRepository.saveAndFlush(message);
        entityManager.refresh(message);

        MessageResponse dto = MessageResponse.from(message);
        if (message.getChannel() != null) {
            publishToRedis(message.getChannel().getId(), WsEvent.EventType.MESSAGE_EDITED, dto);
        }
        return dto;
    }

    @Transactional
    public void deleteMessage(Long userId, Long messageId) {
        Message message = messageRepository.findById(messageId)
                .filter(m -> m.getDeletedAt() == null)
                .orElseThrow(() -> new MessageNotFoundException(messageId));

        boolean isAuthor = message.getAuthor().getId().equals(userId);
        boolean isOwner = false;
        if (message.getWorkspace() != null) {
            isOwner = workspaceMemberRepository
                    .findByWorkspaceIdAndUserIdAndLeftAtIsNull(message.getWorkspace().getId(), userId)
                    .map(m -> m.getRole() == WorkspaceRole.OWNER)
                    .orElse(false);
        }
        if (!isAuthor && !isOwner) {
            throw new MessageForbiddenException(
                    "メッセージの削除は投稿者または OWNER のみ可能です: messageId=" + messageId);
        }

        message.setDeletedAt(OffsetDateTime.now());
        messageRepository.saveAndFlush(message);

        MessageResponse dto = MessageResponse.from(message);
        if (message.getChannel() != null) {
            publishToRedis(message.getChannel().getId(), WsEvent.EventType.MESSAGE_DELETED, dto);
        }
    }

    private void requireChannelMember(Long channelId, Long userId) {
        channelMemberRepository.findByChannelIdAndUserIdAndLeftAtIsNull(channelId, userId)
                .orElseThrow(() -> new MessageForbiddenException(
                        "チャンネルのメンバーではありません: channelId=" + channelId));
    }

    private void publishToRedis(Long channelId, WsEvent.EventType type, MessageResponse payload) {
        try {
            String json = objectMapper.writeValueAsString(new WsEvent(type, payload));
            redisTemplate.convertAndSend(redisChannelPrefix + channelId, json);
        } catch (JsonProcessingException e) {
            log.error("Redis publish failed for channelId={}: {}", channelId, e.getMessage(), e);
        }
    }

    private int clampLimit(Integer limit) {
        if (limit == null || limit <= 0) return DEFAULT_LIMIT;
        return Math.min(limit, MAX_LIMIT);
    }
}
