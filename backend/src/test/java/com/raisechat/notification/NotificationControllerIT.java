package com.raisechat.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raisechat.channel.Channel;
import com.raisechat.channel.ChannelRepository;
import com.raisechat.dm.DmRoom;
import com.raisechat.dm.DmRoomRepository;
import com.raisechat.message.Message;
import com.raisechat.message.MessageRepository;
import com.raisechat.user.User;
import com.raisechat.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// F-14 通知・未読数 API（GET /api/notifications/unread, POST /api/channels|dm/rooms/{id}/read）の結合テスト。
// seed の対応関係（ReactionControllerIT と同じ）:
//   - general  (id=1): keisuke / haruka / ryo / mika / kenta 全員メンバー
//   - dev-backend (id=3): keisuke / haruka / ryo のみ。mika は非メンバー
//   - DM room 1 (ws=1): keisuke ⇔ haruka。mika は非メンバー
//
// 注意: @Transactional は DB のみロールバックする。Redis は永続するため @BeforeEach で unread:* を掃除する。
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class NotificationControllerIT {

    private static final long CH_GENERAL = 1L;
    private static final long CH_DEV_BACKEND = 3L;
    private static final long DM_ROOM = 1L;

    private static final String OWNER = "keisuke";
    private static final String MEMBER = "haruka";
    private static final String NON_MEMBER = "mika";
    private static final String PASSWORD = "password";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ChannelRepository channelRepository;

    @Autowired
    private DmRoomRepository dmRoomRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void clearUnreadCache() {
        Set<String> keys = redisTemplate.keys("unread:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    // ============================================================
    // GET /api/notifications/unread
    // ============================================================

    @Test
    void getUnreadColdRebuildsFromPostgres() throws Exception {
        createChannelMessage(OWNER, CH_GENERAL, "未読になるメッセージ");
        String token = login(MEMBER);
        Long memberId = userRepository.findByUserId(MEMBER).orElseThrow().getId();

        // rebuild の期待値は Postgres の COUNT と一致するはず（seed のノイズに依存しない）
        long expected = messageRepository.countChannelUnread(CH_GENERAL, memberId, 0L);

        assertEquals(expected, unreadFor(token, "channel", CH_GENERAL));
    }

    @Test
    void getUnreadWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/notifications/unread"))
                .andExpect(status().isUnauthorized());
    }

    // ============================================================
    // POST /api/channels/{id}/read
    // ============================================================

    @Test
    void markChannelReadClearsUnread() throws Exception {
        createChannelMessage(OWNER, CH_GENERAL, "既読にする対象");
        String token = login(MEMBER);

        // 同期させて未読が 1 以上あることを確認
        assert unreadFor(token, "channel", CH_GENERAL) >= 1;

        mockMvc.perform(post("/api/channels/" + CH_GENERAL + "/read")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        assertEquals(0L, unreadFor(token, "channel", CH_GENERAL));
    }

    @Test
    void markChannelReadToOlderMessageLeavesResidual() throws Exception {
        Long m1 = createChannelMessage(OWNER, CH_GENERAL, "古い方");
        createChannelMessage(OWNER, CH_GENERAL, "新しい方"); // m2 > m1
        String token = login(MEMBER);

        // 同期（rebuild）
        unreadFor(token, "channel", CH_GENERAL);

        // m1 までを既読にすると、m1 より新しい他人のメッセージ（= m2 の 1 件）が残る
        mockMvc.perform(post("/api/channels/" + CH_GENERAL + "/read")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lastReadMessageId\":" + m1 + "}"))
                .andExpect(status().isNoContent());

        assertEquals(1L, unreadFor(token, "channel", CH_GENERAL));

        // 最新まで既読にするとゼロクリア
        mockMvc.perform(post("/api/channels/" + CH_GENERAL + "/read")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        assertEquals(0L, unreadFor(token, "channel", CH_GENERAL));
    }

    @Test
    void markChannelReadByNonMemberReturns403() throws Exception {
        String token = login(NON_MEMBER); // mika は dev-backend の非メンバー

        mockMvc.perform(post("/api/channels/" + CH_DEV_BACKEND + "/read")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void markChannelReadWithoutTokenReturns401() throws Exception {
        mockMvc.perform(post("/api/channels/" + CH_GENERAL + "/read"))
                .andExpect(status().isUnauthorized());
    }

    // ============================================================
    // 書き込み時ファンアウト（同期済みユーザーの未読が +1 される）
    // ============================================================

    @Test
    void newReplyFansOutToSyncedMember() throws Exception {
        Long parentId = createChannelMessage(OWNER, CH_GENERAL, "スレッド親");
        String memberToken = login(MEMBER);

        // 先に同期しておく（コールドだとファンアウトはスキップされる仕様）
        long before = unreadFor(memberToken, "channel", CH_GENERAL);

        // keisuke が API 経由で返信 → publishCreated → onNewMessage でファンアウト
        String ownerToken = login(OWNER);
        mockMvc.perform(post("/api/messages/" + parentId + "/replies")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"返信です\"}"))
                .andExpect(status().isCreated());

        assertEquals(before + 1, unreadFor(memberToken, "channel", CH_GENERAL));
    }

    // ============================================================
    // POST /api/dm/rooms/{id}/read
    // ============================================================

    @Test
    void markDmReadClearsUnread() throws Exception {
        createDmMessage(OWNER, DM_ROOM, "DM の未読対象");
        String token = login(MEMBER); // haruka は DM room 1 のメンバー

        assert unreadFor(token, "dm", DM_ROOM) >= 1;

        mockMvc.perform(post("/api/dm/rooms/" + DM_ROOM + "/read")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        assertEquals(0L, unreadFor(token, "dm", DM_ROOM));
    }

    @Test
    void markDmReadByNonMemberReturns403() throws Exception {
        String token = login(NON_MEMBER); // mika は DM room 1 の非メンバー

        mockMvc.perform(post("/api/dm/rooms/" + DM_ROOM + "/read")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    // ============================================================
    // helpers
    // ============================================================

    // GET /api/notifications/unread を呼び、指定スコープの未読数を返す（無ければ 0）。
    private long unreadFor(String token, String scopeType, long scopeId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/notifications/unread")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode items = objectMapper.readTree(result.getResponse().getContentAsString()).get("items");
        for (JsonNode item : items) {
            if (scopeType.equals(item.get("scopeType").asText())
                    && item.get("scopeId").asLong() == scopeId) {
                return item.get("unreadCount").asLong();
            }
        }
        return 0L;
    }

    private Long createChannelMessage(String userId, Long channelId, String body) {
        User user = userRepository.findByUserId(userId).orElseThrow();
        Channel channel = channelRepository.findByIdAndDeletedAtIsNull(channelId).orElseThrow();
        Message msg = new Message();
        msg.setWorkspace(channel.getWorkspace());
        msg.setChannel(channel);
        msg.setAuthor(user);
        msg.setBody(body);
        messageRepository.saveAndFlush(msg);
        return msg.getId();
    }

    private Long createDmMessage(String userId, Long dmRoomId, String body) {
        User user = userRepository.findByUserId(userId).orElseThrow();
        DmRoom room = dmRoomRepository.findActiveByIdWithUsers(dmRoomId).orElseThrow();
        Message msg = new Message();
        msg.setWorkspace(room.getWorkspace());
        msg.setDmRoom(room);
        msg.setAuthor(user);
        msg.setBody(body);
        messageRepository.saveAndFlush(msg);
        return msg.getId();
    }

    private String login(String userId) throws Exception {
        String body = """
                {"userId":"%s","password":"%s"}
                """.formatted(userId, PASSWORD);
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("accessToken").asText();
    }
}
