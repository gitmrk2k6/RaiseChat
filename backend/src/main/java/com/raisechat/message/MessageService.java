package com.raisechat.message;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raisechat.channel.Channel;
import com.raisechat.channel.ChannelMemberRepository;
import com.raisechat.channel.ChannelRepository;
import com.raisechat.dm.DmRoom;
import com.raisechat.dm.DmRoomRepository;
import com.raisechat.dm.DmService;
import com.raisechat.message.dto.AddReactionRequest;
import com.raisechat.message.dto.EditMessageRequest;
import com.raisechat.message.dto.MessageListResponse;
import com.raisechat.message.dto.MessageResponse;
import com.raisechat.message.dto.ReactionResponse;
import com.raisechat.message.dto.ReactionResult;
import com.raisechat.message.dto.ReplyMessageRequest;
import com.raisechat.message.dto.SendMessageRequest;
import com.raisechat.message.dto.WsEvent;
import com.raisechat.message.exception.MessageForbiddenException;
import com.raisechat.message.exception.MessageNotFoundException;
import com.raisechat.notification.NotificationService;
import com.raisechat.user.UserRepository;
import com.raisechat.workspace.WorkspaceMemberRepository;
import com.raisechat.workspace.WorkspaceRole;
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
    private final ReactionRepository reactionRepository;
    private final ChannelRepository channelRepository;
    private final ChannelMemberRepository channelMemberRepository;
    private final DmRoomRepository dmRoomRepository;
    private final DmService dmService;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @PersistenceContext
    private EntityManager entityManager;

    @Value("${app.redis.channel-prefix:messages:channel:}")
    private String redisChannelPrefix;

    @Value("${app.redis.dm-prefix:messages:dm:}")
    private String redisDmPrefix;

    @Value("${app.redis.thread-prefix:messages:thread:}")
    private String redisThreadPrefix;

    public MessageService(
            MessageRepository messageRepository,
            ReactionRepository reactionRepository,
            ChannelRepository channelRepository,
            ChannelMemberRepository channelMemberRepository,
            DmRoomRepository dmRoomRepository,
            DmService dmService,
            WorkspaceMemberRepository workspaceMemberRepository,
            UserRepository userRepository,
            NotificationService notificationService,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper
    ) {
        this.messageRepository = messageRepository;
        this.reactionRepository = reactionRepository;
        this.channelRepository = channelRepository;
        this.channelMemberRepository = channelMemberRepository;
        this.dmRoomRepository = dmRoomRepository;
        this.dmService = dmService;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
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

    @Transactional(readOnly = true)
    public MessageListResponse listDmMessages(Long userId, Long dmRoomId, Long cursorId, Integer limit) {
        dmRoomRepository.findActiveByIdWithUsers(dmRoomId)
                .orElseThrow(() -> new MessageNotFoundException(dmRoomId));
        dmService.requireDmMember(dmRoomId, userId);

        int effectiveLimit = clampLimit(limit);
        long effectiveCursor = cursorId != null ? cursorId : 0L;

        List<Message> rows = messageRepository.findDmMessagesBefore(
                dmRoomId, effectiveCursor, PageRequest.ofSize(effectiveLimit + 1));

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
        publishCreated(message, dto);
        return dto;
    }

    @Transactional
    public MessageResponse sendDmMessage(Long userId, Long dmRoomId, SendMessageRequest req) {
        DmRoom dmRoom = dmRoomRepository.findActiveByIdWithUsers(dmRoomId)
                .orElseThrow(() -> new MessageNotFoundException(dmRoomId));
        dmService.requireDmMember(dmRoomId, userId);

        Message message = new Message();
        message.setWorkspace(dmRoom.getWorkspace());
        message.setDmRoom(dmRoom);
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
        publishCreated(message, dto);
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
        publishEditDeleteEvent(message, WsEvent.EventType.MESSAGE_EDITED, dto);
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
        publishEditDeleteEvent(message, WsEvent.EventType.MESSAGE_DELETED, dto);
    }

    @Transactional(readOnly = true)
    public MessageListResponse listReplies(Long userId, Long parentId, Long cursorId, Integer limit) {
        Message parent = messageRepository.findById(parentId)
                .filter(m -> m.getDeletedAt() == null)
                .orElseThrow(() -> new MessageNotFoundException(parentId));
        requireMessageAccess(parent, userId);

        int effectiveLimit = clampLimit(limit);
        long effectiveCursor = cursorId != null ? cursorId : 0L;

        List<Message> rows = messageRepository.findRepliesAfter(
                parentId, effectiveCursor, PageRequest.ofSize(effectiveLimit + 1));

        boolean hasMore = rows.size() > effectiveLimit;
        List<Message> page = hasMore ? rows.subList(0, effectiveLimit) : rows;

        List<MessageResponse> items = page.stream().map(MessageResponse::from).toList();
        String nextCursor = hasMore ? String.valueOf(page.get(page.size() - 1).getId()) : null;

        return new MessageListResponse(items, nextCursor, hasMore);
    }

    @Transactional
    public MessageResponse createReply(Long userId, Long parentId, ReplyMessageRequest req) {
        Message parent = messageRepository.findById(parentId)
                .filter(m -> m.getDeletedAt() == null)
                .orElseThrow(() -> new MessageNotFoundException(parentId));
        requireMessageAccess(parent, userId);

        // スレッドは 1 階層に固定（Slack セマンティクス）。返信への返信はスレッドの root に付け替える。
        Message root = parent.getParent() != null ? parent.getParent() : parent;

        Message message = new Message();
        message.setWorkspace(root.getWorkspace());
        message.setChannel(root.getChannel());
        message.setDmRoom(root.getDmRoom());
        message.setParent(root);
        message.setAuthor(userRepository.getReferenceById(userId));
        message.setBody(req.body());

        messageRepository.saveAndFlush(message);
        entityManager.refresh(message);

        MessageResponse dto = MessageResponse.from(message);
        publishCreated(message, dto);
        return dto;
    }

    @Transactional
    public ReactionResult addReaction(Long userId, Long messageId, AddReactionRequest req) {
        Message message = loadActiveMessage(messageId);
        requireMessageAccess(message, userId);

        String emoji = req.emoji();
        boolean created = reactionRepository
                .findByMessageIdAndUserIdAndEmoji(messageId, userId, emoji)
                .isEmpty();
        if (created) {
            Reaction reaction = new Reaction();
            reaction.setMessage(message);
            reaction.setUser(userRepository.getReferenceById(userId));
            reaction.setEmoji(emoji);
            reactionRepository.saveAndFlush(reaction);
        }

        ReactionResponse dto = buildReactionResponse(messageId, emoji);
        // 既に付与済み（冪等な 200）のときは配信しない。重複イベントで他クライアントのカウントがずれるのを防ぐ。
        if (created) {
            publishReactionEvent(message, WsEvent.EventType.REACTION_ADDED, dto);
        }
        return new ReactionResult(dto, created);
    }

    @Transactional
    public void removeReaction(Long userId, Long messageId, String emoji) {
        Message message = loadActiveMessage(messageId);
        requireMessageAccess(message, userId);

        var existing = reactionRepository.findByMessageIdAndUserIdAndEmoji(messageId, userId, emoji);
        if (existing.isEmpty()) {
            return; // 冪等: 付与されていなければ何もせず 204 を返す
        }
        reactionRepository.delete(existing.get());
        reactionRepository.flush();

        ReactionResponse dto = buildReactionResponse(messageId, emoji);
        publishReactionEvent(message, WsEvent.EventType.REACTION_REMOVED, dto);
    }

    private Message loadActiveMessage(Long messageId) {
        return messageRepository.findById(messageId)
                .filter(m -> m.getDeletedAt() == null)
                .orElseThrow(() -> new MessageNotFoundException(messageId));
    }

    private ReactionResponse buildReactionResponse(Long messageId, String emoji) {
        List<Long> userIds = reactionRepository.findUserIdsByMessageIdAndEmoji(messageId, emoji);
        return new ReactionResponse(messageId, emoji, userIds.size(), userIds);
    }

    // スレッド返信の閲覧 / 投稿は、親メッセージが属するチャンネル / DM のメンバーシップを継承する。
    private void requireMessageAccess(Message parent, Long userId) {
        if (parent.getChannel() != null) {
            requireChannelMember(parent.getChannel().getId(), userId);
        } else if (parent.getDmRoom() != null) {
            dmService.requireDmMember(parent.getDmRoom().getId(), userId);
        }
    }

    private void requireChannelMember(Long channelId, Long userId) {
        channelMemberRepository.findByChannelIdAndUserIdAndLeftAtIsNull(channelId, userId)
                .orElseThrow(() -> new MessageForbiddenException(
                        "チャンネルのメンバーではありません: channelId=" + channelId));
    }

    // 親を持つメッセージ（スレッド返信）はスレッドトピックへ、そうでなければチャンネル / DM トピックへ配信する。
    // 配信先を一本化することで、REST 経由の返信と WebSocket 経由の親付き送信が同じトピックに乗る。
    private void publishCreated(Message message, MessageResponse dto) {
        publishMessageEvent(message, WsEvent.EventType.MESSAGE_CREATED, dto);
        // F-14: 新着を受信者の未読カウンタへファンアウト（同一トランザクション内）。
        notificationService.onNewMessage(message);
    }

    private void publishEditDeleteEvent(Message message, WsEvent.EventType type, MessageResponse dto) {
        publishMessageEvent(message, type, dto);
    }

    // リアクションも、対象メッセージと同じトピック（返信ならスレッド、通常はチャンネル / DM）へ流す。
    private void publishReactionEvent(Message message, WsEvent.EventType type, ReactionResponse dto) {
        publishMessageEvent(message, type, dto);
    }

    private void publishMessageEvent(Message message, WsEvent.EventType type, Object payload) {
        String topic = resolveTopic(message);
        if (topic != null) {
            publish(topic, type, payload);
        }
    }

    private String resolveTopic(Message message) {
        if (message.getParent() != null) {
            return redisThreadPrefix + message.getParent().getId();
        } else if (message.getChannel() != null) {
            return redisChannelPrefix + message.getChannel().getId();
        } else if (message.getDmRoom() != null) {
            return redisDmPrefix + message.getDmRoom().getId();
        }
        return null;
    }

    private void publish(String topic, WsEvent.EventType type, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(new WsEvent(type, payload));
            redisTemplate.convertAndSend(topic, json);
        } catch (JsonProcessingException e) {
            log.error("Redis publish failed for topic={}: {}", topic, e.getMessage(), e);
        }
    }

    private int clampLimit(Integer limit) {
        if (limit == null || limit <= 0) return DEFAULT_LIMIT;
        return Math.min(limit, MAX_LIMIT);
    }
}
