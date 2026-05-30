package com.raisechat.notification;

import com.raisechat.channel.Channel;
import com.raisechat.channel.ChannelMemberRepository;
import com.raisechat.channel.ChannelRepository;
import com.raisechat.channel.exception.ChannelForbiddenException;
import com.raisechat.channel.exception.ChannelNotFoundException;
import com.raisechat.dm.DmRoom;
import com.raisechat.dm.DmRoomRepository;
import com.raisechat.dm.DmService;
import com.raisechat.dm.exception.DmNotFoundException;
import com.raisechat.message.MentionRepository;
import com.raisechat.message.Message;
import com.raisechat.message.MessageRepository;
import com.raisechat.message.ReadState;
import com.raisechat.message.ReadStateRepository;
import com.raisechat.message.exception.MessageNotFoundException;
import com.raisechat.notification.dto.MarkReadRequest;
import com.raisechat.notification.dto.NotificationEvent;
import com.raisechat.notification.dto.UnreadItem;
import com.raisechat.notification.dto.UnreadResponse;
import com.raisechat.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * F-14 未読数の中核。Postgres（read_states）を真実、Redis（unread:{userId}）をキャッシュとする
 * 2 層構成で、書き込み時ファンアウト + cache-aside rebuild を実装する。
 */
@Service
public class NotificationService {

    private final ChannelRepository channelRepository;
    private final ChannelMemberRepository channelMemberRepository;
    private final DmRoomRepository dmRoomRepository;
    private final DmService dmService;
    private final MessageRepository messageRepository;
    private final MentionRepository mentionRepository;
    private final ReadStateRepository readStateRepository;
    private final UserRepository userRepository;
    private final UnreadCounterStore store;
    private final NotificationPublisher publisher;

    public NotificationService(
            ChannelRepository channelRepository,
            ChannelMemberRepository channelMemberRepository,
            DmRoomRepository dmRoomRepository,
            DmService dmService,
            MessageRepository messageRepository,
            MentionRepository mentionRepository,
            ReadStateRepository readStateRepository,
            UserRepository userRepository,
            UnreadCounterStore store,
            NotificationPublisher publisher
    ) {
        this.channelRepository = channelRepository;
        this.channelMemberRepository = channelMemberRepository;
        this.dmRoomRepository = dmRoomRepository;
        this.dmService = dmService;
        this.messageRepository = messageRepository;
        this.mentionRepository = mentionRepository;
        this.readStateRepository = readStateRepository;
        this.userRepository = userRepository;
        this.store = store;
        this.publisher = publisher;
    }

    // ============================================================
    // 書き込み時ファンアウト（MessageService から呼ばれる）
    // ============================================================

    /**
     * 新着メッセージを受信者全員の未読カウンタへ加算し、WS 通知を発行する。
     * メンション先（mentionedUserIds）に含まれる受信者は、別軸のメンションカウンタも +1 する。
     */
    @Transactional
    public void onNewMessage(Message message, List<Long> mentionedUserIds) {
        Set<Long> mentioned = new HashSet<>(mentionedUserIds);
        if (message.getChannel() != null) {
            fanOutChannel(message.getChannel().getId(), message.getAuthor().getId(), mentioned);
        } else if (message.getDmRoom() != null) {
            fanOutDm(message.getDmRoom().getId(), message.getAuthor().getId(), mentioned);
        }
    }

    private void fanOutChannel(Long channelId, Long authorId, Set<Long> mentioned) {
        String field = UnreadCounterStore.channelField(channelId);
        for (Long memberId : channelMemberRepository.findActiveUserIdsByChannelId(channelId)) {
            incrementAndNotify(memberId, authorId, field, UnreadItem.SCOPE_CHANNEL, channelId,
                    mentioned.contains(memberId));
        }
    }

    private void fanOutDm(Long dmRoomId, Long authorId, Set<Long> mentioned) {
        DmRoom room = dmRoomRepository.findActiveByIdWithUsers(dmRoomId).orElse(null);
        if (room == null) {
            return;
        }
        String field = UnreadCounterStore.dmField(dmRoomId);
        for (Long memberId : List.of(room.getUserA().getId(), room.getUserB().getId())) {
            incrementAndNotify(memberId, authorId, field, UnreadItem.SCOPE_DM, dmRoomId,
                    mentioned.contains(memberId));
        }
    }

    private void incrementAndNotify(
            Long memberId, Long authorId, String field, String scopeType, Long scopeId, boolean isMentioned) {
        if (memberId.equals(authorId)) {
            return; // 自分の投稿は自分の未読にならない
        }
        // キャッシュが未構築（コールド）なら加算しない。次回 GET の rebuild で正しく算出するため、
        // 中途半端な値で上書きしてバックログを取りこぼすのを防ぐ。
        if (!store.isSynced(memberId)) {
            return;
        }
        long count = store.increment(memberId, field);
        // メンション先なら +1、そうでなければ現在値をそのまま WS イベントへ載せる（イベントを自己完結させる）。
        long mentionCount = isMentioned
                ? store.incrementMention(memberId, field)
                : store.getMention(memberId, field);
        publisher.publish(memberId, new NotificationEvent(
                NotificationEvent.EventType.UNREAD_UPDATED, scopeType, scopeId, count, mentionCount));
    }

    // ============================================================
    // 既読クリア
    // ============================================================

    @Transactional
    public void markChannelRead(Long userId, Long channelId, MarkReadRequest req) {
        Channel channel = channelRepository.findByIdAndDeletedAtIsNull(channelId)
                .orElseThrow(() -> new ChannelNotFoundException(channelId));
        requireChannelMember(channelId, userId);

        long targetId = resolveChannelTarget(channelId, req);
        String field = UnreadCounterStore.channelField(channelId);
        if (targetId == 0L) {
            store.set(userId, field, 0L); // メッセージ皆無。未読 0 を保証して終了。
            store.setMention(userId, field, 0L);
            return;
        }

        ReadState state = upsertChannelReadState(userId, channel, targetId);
        long lastReadId = state.getLastReadMessage().getId();
        long residual = messageRepository.countChannelUnread(channelId, userId, lastReadId);
        long mentionResidual = mentionRepository.countChannelMentionUnread(channelId, userId, lastReadId);
        store.set(userId, field, residual);
        store.setMention(userId, field, mentionResidual);
        publishRead(userId, UnreadItem.SCOPE_CHANNEL, channelId, residual, mentionResidual);
    }

    @Transactional
    public void markDmRead(Long userId, Long dmRoomId, MarkReadRequest req) {
        DmRoom room = dmRoomRepository.findActiveByIdWithUsers(dmRoomId)
                .orElseThrow(() -> new DmNotFoundException("DM ルームが見つかりません: dmRoomId=" + dmRoomId));
        dmService.requireDmMember(dmRoomId, userId);

        long targetId = resolveDmTarget(dmRoomId, req);
        String field = UnreadCounterStore.dmField(dmRoomId);
        if (targetId == 0L) {
            store.set(userId, field, 0L);
            store.setMention(userId, field, 0L);
            return;
        }

        ReadState state = upsertDmReadState(userId, room, targetId);
        long lastReadId = state.getLastReadMessage().getId();
        long residual = messageRepository.countDmUnread(dmRoomId, userId, lastReadId);
        long mentionResidual = mentionRepository.countDmMentionUnread(dmRoomId, userId, lastReadId);
        store.set(userId, field, residual);
        store.setMention(userId, field, mentionResidual);
        publishRead(userId, UnreadItem.SCOPE_DM, dmRoomId, residual, mentionResidual);
    }

    // ============================================================
    // 未読一覧取得（cache-aside）
    // ============================================================

    @Transactional
    public UnreadResponse getUnread(Long userId) {
        if (!store.isSynced(userId)) {
            rebuild(userId);
        }
        Map<String, Long> unreadByField = store.getAll(userId);
        Map<String, Long> mentionByField = store.getAllMentions(userId);
        List<UnreadItem> items = new ArrayList<>();
        unreadByField.forEach((field, count) -> {
            long mention = mentionByField.getOrDefault(field, 0L);
            if (count > 0 || mention > 0) {
                UnreadItem item = toItem(field, count, mention);
                if (item != null) {
                    items.add(item);
                }
            }
        });
        return new UnreadResponse(items);
    }

    /** キャッシュミス時に Postgres から未読数・メンション数を再構築して Redis へ書き込む。 */
    private void rebuild(Long userId) {
        Map<String, Long> snapshot = new HashMap<>();
        Map<String, Long> mentionSnapshot = new HashMap<>();

        Map<Long, Long> channelLastRead = new HashMap<>();
        for (ReadState rs : readStateRepository.findByUserIdAndChannelIdIsNotNull(userId)) {
            channelLastRead.put(rs.getChannel().getId(), rs.getLastReadMessage().getId());
        }
        for (Long channelId : channelMemberRepository.findActiveChannelIdsByUserId(userId)) {
            long lastRead = channelLastRead.getOrDefault(channelId, 0L);
            String field = UnreadCounterStore.channelField(channelId);
            long count = messageRepository.countChannelUnread(channelId, userId, lastRead);
            if (count > 0) {
                snapshot.put(field, count);
            }
            long mention = mentionRepository.countChannelMentionUnread(channelId, userId, lastRead);
            if (mention > 0) {
                mentionSnapshot.put(field, mention);
            }
        }

        Map<Long, Long> dmLastRead = new HashMap<>();
        for (ReadState rs : readStateRepository.findByUserIdAndDmRoomIdIsNotNull(userId)) {
            dmLastRead.put(rs.getDmRoom().getId(), rs.getLastReadMessage().getId());
        }
        for (Long dmRoomId : dmRoomRepository.findRoomIdsByUserId(userId)) {
            long lastRead = dmLastRead.getOrDefault(dmRoomId, 0L);
            String field = UnreadCounterStore.dmField(dmRoomId);
            long count = messageRepository.countDmUnread(dmRoomId, userId, lastRead);
            if (count > 0) {
                snapshot.put(field, count);
            }
            long mention = mentionRepository.countDmMentionUnread(dmRoomId, userId, lastRead);
            if (mention > 0) {
                mentionSnapshot.put(field, mention);
            }
        }

        store.writeSnapshot(userId, snapshot, mentionSnapshot);
    }

    // ============================================================
    // helpers
    // ============================================================

    private long resolveChannelTarget(Long channelId, MarkReadRequest req) {
        if (req != null && req.lastReadMessageId() != null) {
            Message m = loadActiveMessage(req.lastReadMessageId());
            if (m.getChannel() == null || !m.getChannel().getId().equals(channelId)) {
                throw new MessageNotFoundException(req.lastReadMessageId());
            }
            return m.getId();
        }
        Long max = messageRepository.findMaxIdByChannelId(channelId);
        return max == null ? 0L : max;
    }

    private long resolveDmTarget(Long dmRoomId, MarkReadRequest req) {
        if (req != null && req.lastReadMessageId() != null) {
            Message m = loadActiveMessage(req.lastReadMessageId());
            if (m.getDmRoom() == null || !m.getDmRoom().getId().equals(dmRoomId)) {
                throw new MessageNotFoundException(req.lastReadMessageId());
            }
            return m.getId();
        }
        Long max = messageRepository.findMaxIdByDmRoomId(dmRoomId);
        return max == null ? 0L : max;
    }

    private Message loadActiveMessage(Long messageId) {
        return messageRepository.findById(messageId)
                .filter(m -> m.getDeletedAt() == null)
                .orElseThrow(() -> new MessageNotFoundException(messageId));
    }

    private ReadState upsertChannelReadState(Long userId, Channel channel, long targetId) {
        ReadState state = readStateRepository.findByUserIdAndChannelId(userId, channel.getId()).orElse(null);
        if (state == null) {
            state = new ReadState();
            state.setUser(userRepository.getReferenceById(userId));
            state.setChannel(channel);
            state.setLastReadMessage(messageRepository.getReferenceById(targetId));
        } else if (targetId > state.getLastReadMessage().getId()) {
            state.setLastReadMessage(messageRepository.getReferenceById(targetId));
        }
        return readStateRepository.saveAndFlush(state);
    }

    private ReadState upsertDmReadState(Long userId, DmRoom room, long targetId) {
        ReadState state = readStateRepository.findByUserIdAndDmRoomId(userId, room.getId()).orElse(null);
        if (state == null) {
            state = new ReadState();
            state.setUser(userRepository.getReferenceById(userId));
            state.setDmRoom(room);
            state.setLastReadMessage(messageRepository.getReferenceById(targetId));
        } else if (targetId > state.getLastReadMessage().getId()) {
            state.setLastReadMessage(messageRepository.getReferenceById(targetId));
        }
        return readStateRepository.saveAndFlush(state);
    }

    private void requireChannelMember(Long channelId, Long userId) {
        channelMemberRepository.findByChannelIdAndUserIdAndLeftAtIsNull(channelId, userId)
                .orElseThrow(() -> new ChannelForbiddenException(
                        "チャンネルのメンバーではありません: channelId=" + channelId));
    }

    private void publishRead(Long userId, String scopeType, Long scopeId, long residual, long mentionResidual) {
        // イベント種別は総未読基準のまま（メンションは内数）。
        NotificationEvent.EventType type = residual == 0
                ? NotificationEvent.EventType.UNREAD_CLEARED
                : NotificationEvent.EventType.UNREAD_UPDATED;
        publisher.publish(userId, new NotificationEvent(type, scopeType, scopeId, residual, mentionResidual));
    }

    private UnreadItem toItem(String field, long count, long mentionCount) {
        int sep = field.indexOf(':');
        if (sep < 0) {
            return null;
        }
        String scopeType = field.substring(0, sep);
        try {
            Long scopeId = Long.parseLong(field.substring(sep + 1));
            return new UnreadItem(scopeType, scopeId, count, mentionCount);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
