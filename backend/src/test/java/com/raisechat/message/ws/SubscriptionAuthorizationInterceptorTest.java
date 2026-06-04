package com.raisechat.message.ws;

import com.raisechat.auth.jwt.AuthenticatedUser;
import com.raisechat.channel.ChannelMember;
import com.raisechat.channel.ChannelMemberRepository;
import com.raisechat.dm.DmMemberRepository;
import com.raisechat.message.MessageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/**
 * SUBSCRIBE 認可ロジックの単体テスト。CONNECT 済みのセッション利用者が、所属していない
 * チャンネル / DM / スレッドを購読しようとすると拒否されることを確認する。
 */
@ExtendWith(MockitoExtension.class)
class SubscriptionAuthorizationInterceptorTest {

    private static final long USER_ID = 10L;

    @Mock
    private ChannelMemberRepository channelMemberRepository;
    @Mock
    private DmMemberRepository dmMemberRepository;
    @Mock
    private MessageRepository messageRepository;

    @InjectMocks
    private SubscriptionAuthorizationInterceptor interceptor;

    private final MessageChannel channel = mock(MessageChannel.class);

    private Message<byte[]> subscribe(String destination, Long userId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        if (userId != null) {
            accessor.setUser(new UsernamePasswordAuthenticationToken(
                    new AuthenticatedUser(userId, "user" + userId), null, List.of()));
        }
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Test
    void allowsChannelMemberToSubscribe() {
        lenient().when(channelMemberRepository.findByChannelIdAndUserIdAndLeftAtIsNull(5L, USER_ID))
                .thenReturn(Optional.of(mock(ChannelMember.class)));

        Message<byte[]> msg = subscribe("/topic/channels/5", USER_ID);
        assertDoesNotThrow(() -> interceptor.preSend(msg, channel));
    }

    @Test
    void rejectsNonMemberChannelSubscribe() {
        lenient().when(channelMemberRepository.findByChannelIdAndUserIdAndLeftAtIsNull(5L, USER_ID))
                .thenReturn(Optional.empty());

        Message<byte[]> msg = subscribe("/topic/channels/5", USER_ID);
        assertThatThrownBy(() -> interceptor.preSend(msg, channel))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void allowsDmMemberToSubscribe() {
        lenient().when(dmMemberRepository.existsByDmRoomIdAndUserId(7L, USER_ID)).thenReturn(true);

        Message<byte[]> msg = subscribe("/topic/dm/7", USER_ID);
        assertDoesNotThrow(() -> interceptor.preSend(msg, channel));
    }

    @Test
    void rejectsNonMemberDmSubscribe() {
        lenient().when(dmMemberRepository.existsByDmRoomIdAndUserId(7L, USER_ID)).thenReturn(false);

        Message<byte[]> msg = subscribe("/topic/dm/7", USER_ID);
        assertThatThrownBy(() -> interceptor.preSend(msg, channel))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void allowsThreadSubscribeWhenMemberOfParentChannel() {
        lenient().when(messageRepository.findChannelIdById(99L)).thenReturn(Optional.of(5L));
        lenient().when(channelMemberRepository.findByChannelIdAndUserIdAndLeftAtIsNull(5L, USER_ID))
                .thenReturn(Optional.of(mock(ChannelMember.class)));

        Message<byte[]> msg = subscribe("/topic/threads/99", USER_ID);
        assertDoesNotThrow(() -> interceptor.preSend(msg, channel));
    }

    @Test
    void rejectsThreadSubscribeWhenNotMemberOfParentChannel() {
        lenient().when(messageRepository.findChannelIdById(99L)).thenReturn(Optional.of(5L));
        lenient().when(channelMemberRepository.findByChannelIdAndUserIdAndLeftAtIsNull(5L, USER_ID))
                .thenReturn(Optional.empty());

        Message<byte[]> msg = subscribe("/topic/threads/99", USER_ID);
        assertThatThrownBy(() -> interceptor.preSend(msg, channel))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void allowsNonGuardedDestinationForAuthenticatedUser() {
        // presence は購読単位の所属チェック対象外（認証済みなら許可）。
        Message<byte[]> msg = subscribe("/topic/presence", USER_ID);
        assertDoesNotThrow(() -> interceptor.preSend(msg, channel));
    }

    @Test
    void rejectsSubscribeWithoutAuthenticatedUser() {
        Message<byte[]> msg = subscribe("/topic/channels/5", null);
        assertThatThrownBy(() -> interceptor.preSend(msg, channel))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void passesThroughNonSubscribeFrames() {
        // SUBSCRIBE 以外（例: CONNECT）は素通り。メッセージがそのまま返る。
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        Message<byte[]> msg = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        assertThat(interceptor.preSend(msg, channel)).isSameAs(msg);
    }
}
