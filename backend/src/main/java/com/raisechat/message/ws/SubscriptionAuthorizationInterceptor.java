package com.raisechat.message.ws;

import com.raisechat.auth.jwt.AuthenticatedUser;
import com.raisechat.channel.ChannelMemberRepository;
import com.raisechat.dm.DmMemberRepository;
import com.raisechat.message.MessageRepository;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.Optional;

/**
 * SUBSCRIBE フレームの認可を担う。CONNECT 時の {@link JwtChannelInterceptor} で「誰か」は確定するが、
 * 「その宛先を購読してよいか」は別問題。ここでチャンネル / DM / スレッドの所属を検証し、
 * 非メンバーの購読を STOMP ERROR で拒否する。
 *
 * <p>宛先 ID は BIGINT 連番で推測しやすく、購読チェックが無いと ID を当てるだけで
 * プライベートチャンネルのリアルタイム投稿を覗けてしまうため、REST 側の認可と対称に WebSocket でも守る。
 *
 * <p>認可対象の宛先:
 * <ul>
 *   <li>{@code /topic/channels/{channelId}} — チャンネルの現メンバー（left_at IS NULL）のみ</li>
 *   <li>{@code /topic/dm/{dmRoomId}} — その DM の当事者のみ</li>
 *   <li>{@code /topic/threads/{parentMessageId}} — 親メッセージが属すチャンネル / DM のメンバーのみ</li>
 * </ul>
 * それ以外（{@code /topic/presence} やユーザー個別キュー {@code /user/**}）は認証済みなら許可する。
 */
@Component
public class SubscriptionAuthorizationInterceptor implements ChannelInterceptor {

    private static final String CHANNELS_PREFIX = "/topic/channels/";
    private static final String DM_PREFIX = "/topic/dm/";
    private static final String THREADS_PREFIX = "/topic/threads/";

    private final ChannelMemberRepository channelMemberRepository;
    private final DmMemberRepository dmMemberRepository;
    private final MessageRepository messageRepository;

    public SubscriptionAuthorizationInterceptor(
            ChannelMemberRepository channelMemberRepository,
            DmMemberRepository dmMemberRepository,
            MessageRepository messageRepository) {
        this.channelMemberRepository = channelMemberRepository;
        this.dmMemberRepository = dmMemberRepository;
        this.messageRepository = messageRepository;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || !StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            return message;
        }

        String destination = accessor.getDestination();
        if (destination == null) {
            return message;
        }

        Long userId = currentUserId(accessor);
        if (userId == null) {
            throw new MessagingException("認証されていないため購読できません");
        }

        if (destination.startsWith(CHANNELS_PREFIX)) {
            Long channelId = parseId(destination.substring(CHANNELS_PREFIX.length()));
            if (!isChannelMember(channelId, userId)) {
                throw new MessagingException("このチャンネルの購読権限がありません: " + destination);
            }
        } else if (destination.startsWith(DM_PREFIX)) {
            Long dmRoomId = parseId(destination.substring(DM_PREFIX.length()));
            if (!isDmMember(dmRoomId, userId)) {
                throw new MessagingException("この DM の購読権限がありません: " + destination);
            }
        } else if (destination.startsWith(THREADS_PREFIX)) {
            Long parentMessageId = parseId(destination.substring(THREADS_PREFIX.length()));
            if (!canAccessThread(parentMessageId, userId)) {
                throw new MessagingException("このスレッドの購読権限がありません: " + destination);
            }
        }
        // 上記以外（presence・個人キュー等）は認証済みなら許可。
        return message;
    }

    private Long currentUserId(StompHeaderAccessor accessor) {
        Principal user = accessor.getUser();
        if (user instanceof Authentication auth
                && auth.getPrincipal() instanceof AuthenticatedUser principal) {
            return principal.id();
        }
        return null;
    }

    /** 宛先の ID 部分を取り出す。末尾にサブパスが続く場合は最初のセグメントだけを使う。 */
    private Long parseId(String raw) {
        int slash = raw.indexOf('/');
        String head = slash >= 0 ? raw.substring(0, slash) : raw;
        try {
            return Long.valueOf(head);
        } catch (NumberFormatException e) {
            throw new MessagingException("購読先の指定が不正です: " + raw);
        }
    }

    private boolean isChannelMember(Long channelId, Long userId) {
        return channelMemberRepository
                .findByChannelIdAndUserIdAndLeftAtIsNull(channelId, userId)
                .isPresent();
    }

    private boolean isDmMember(Long dmRoomId, Long userId) {
        return dmMemberRepository.existsByDmRoomIdAndUserId(dmRoomId, userId);
    }

    private boolean canAccessThread(Long parentMessageId, Long userId) {
        Optional<Long> channelId = messageRepository.findChannelIdById(parentMessageId);
        if (channelId.isPresent()) {
            return isChannelMember(channelId.get(), userId);
        }
        Optional<Long> dmRoomId = messageRepository.findDmRoomIdById(parentMessageId);
        return dmRoomId.isPresent() && isDmMember(dmRoomId.get(), userId);
    }
}
