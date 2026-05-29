package com.raisechat.message;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raisechat.channel.Channel;
import com.raisechat.channel.ChannelRepository;
import com.raisechat.dm.DmRoom;
import com.raisechat.dm.DmRoomRepository;
import com.raisechat.user.User;
import com.raisechat.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// F-11 絵文字リアクション API（POST/DELETE /api/messages/{id}/reactions[/{emoji}]）の結合テスト。
// seed の対応関係:
//   - general  (id=1): keisuke / haruka / ryo / mika / kenta 全員メンバー
//   - dev-backend (id=3): keisuke / haruka / ryo のみ。mika は非メンバー
//   - DM room 1 (ws=1): keisuke ⇔ haruka。mika は非メンバー
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class ReactionControllerIT {

    private static final long CH_GENERAL = 1L;
    private static final long CH_DEV_BACKEND = 3L;
    private static final long DM_ROOM = 1L;

    private static final String OWNER = "keisuke";
    private static final String MEMBER = "haruka";
    private static final String NON_MEMBER = "mika"; // dev-backend / DM room 1 の非メンバー
    private static final String PASSWORD = "password";

    private static final String THUMBS_UP = "👍";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private ReactionRepository reactionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ChannelRepository channelRepository;

    @Autowired
    private DmRoomRepository dmRoomRepository;

    // ============================================================
    // POST /api/messages/{id}/reactions
    // ============================================================

    @Test
    void addReactionHappyPathReturns201() throws Exception {
        Long messageId = createChannelMessage(OWNER, CH_GENERAL, "リアクション対象");
        String token = login(MEMBER);

        mockMvc.perform(post("/api/messages/" + messageId + "/reactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emoji\":\"" + THUMBS_UP + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.messageId").value(messageId))
                .andExpect(jsonPath("$.emoji").value(THUMBS_UP))
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.userIds.length()").value(1));
    }

    @Test
    void addReactionToDmMessageHappyPath() throws Exception {
        Long messageId = createDmMessage(OWNER, DM_ROOM, "DM のリアクション対象");
        String token = login(MEMBER); // haruka は DM room 1 のメンバー

        mockMvc.perform(post("/api/messages/" + messageId + "/reactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emoji\":\"" + THUMBS_UP + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.count").value(1));
    }

    @Test
    void addDuplicateReactionReturns200AndKeepsCount() throws Exception {
        Long messageId = createChannelMessage(OWNER, CH_GENERAL, "重複付与対象");
        String token = login(MEMBER);

        addReactionViaApi(token, messageId, THUMBS_UP, status().isCreated());

        // 同一ユーザー・同一 emoji の再付与は冪等に 200、count は 1 のまま
        mockMvc.perform(post("/api/messages/" + messageId + "/reactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emoji\":\"" + THUMBS_UP + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));
    }

    @Test
    void twoUsersSameEmojiCountIsTwo() throws Exception {
        Long messageId = createChannelMessage(OWNER, CH_GENERAL, "二人で付与");

        addReactionViaApi(login(OWNER), messageId, THUMBS_UP, status().isCreated());

        mockMvc.perform(post("/api/messages/" + messageId + "/reactions")
                        .header("Authorization", "Bearer " + login(MEMBER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emoji\":\"" + THUMBS_UP + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.count").value(2))
                .andExpect(jsonPath("$.userIds.length()").value(2));
    }

    @Test
    void addReactionByNonMemberReturns403() throws Exception {
        Long messageId = createChannelMessage(OWNER, CH_DEV_BACKEND, "dev-backend のメッセージ");
        String token = login(NON_MEMBER); // mika は dev-backend の非メンバー

        mockMvc.perform(post("/api/messages/" + messageId + "/reactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emoji\":\"" + THUMBS_UP + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void addReactionToDeletedMessageReturns404() throws Exception {
        Long messageId = createChannelMessage(OWNER, CH_GENERAL, "削除されるメッセージ");
        Message message = messageRepository.findById(messageId).orElseThrow();
        message.setDeletedAt(java.time.OffsetDateTime.now());
        messageRepository.saveAndFlush(message);

        mockMvc.perform(post("/api/messages/" + messageId + "/reactions")
                        .header("Authorization", "Bearer " + login(OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emoji\":\"" + THUMBS_UP + "\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void addReactionWithBlankEmojiReturns422() throws Exception {
        Long messageId = createChannelMessage(OWNER, CH_GENERAL, "バリデーション対象");

        mockMvc.perform(post("/api/messages/" + messageId + "/reactions")
                        .header("Authorization", "Bearer " + login(OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emoji\":\"\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.title").value("Validation Failed"));
    }

    @Test
    void addReactionWithoutTokenReturns401() throws Exception {
        Long messageId = createChannelMessage(OWNER, CH_GENERAL, "トークンなし対象");

        mockMvc.perform(post("/api/messages/" + messageId + "/reactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emoji\":\"" + THUMBS_UP + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ============================================================
    // DELETE /api/messages/{id}/reactions/{emoji}
    // ============================================================

    @Test
    void removeReactionReturns204AndClearsCount() throws Exception {
        Long messageId = createChannelMessage(OWNER, CH_GENERAL, "解除対象");
        String token = login(MEMBER);
        addReactionViaApi(token, messageId, THUMBS_UP, status().isCreated());

        mockMvc.perform(delete("/api/messages/{id}/reactions/{emoji}", messageId, THUMBS_UP)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        assert reactionRepository.findUserIdsByMessageIdAndEmoji(messageId, THUMBS_UP).isEmpty();
    }

    @Test
    void removeNonExistentReactionReturns204() throws Exception {
        Long messageId = createChannelMessage(OWNER, CH_GENERAL, "未付与の解除");
        String token = login(MEMBER);

        // 付与していなくても冪等に 204
        mockMvc.perform(delete("/api/messages/{id}/reactions/{emoji}", messageId, THUMBS_UP)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    void removeReactionByNonMemberReturns403() throws Exception {
        Long messageId = createChannelMessage(OWNER, CH_DEV_BACKEND, "dev-backend の解除対象");
        String token = login(NON_MEMBER);

        mockMvc.perform(delete("/api/messages/{id}/reactions/{emoji}", messageId, THUMBS_UP)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void removeReactionWithoutTokenReturns401() throws Exception {
        Long messageId = createChannelMessage(OWNER, CH_GENERAL, "トークンなし解除");

        mockMvc.perform(delete("/api/messages/{id}/reactions/{emoji}", messageId, THUMBS_UP))
                .andExpect(status().isUnauthorized());
    }

    // ============================================================
    // helpers
    // ============================================================

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

    private void addReactionViaApi(String token, Long messageId, String emoji,
                                   org.springframework.test.web.servlet.ResultMatcher expectedStatus) throws Exception {
        mockMvc.perform(post("/api/messages/" + messageId + "/reactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emoji\":\"" + emoji + "\"}"))
                .andExpect(expectedStatus);
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
