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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// F-08 スレッド API（GET/POST /api/messages/{parentId}/replies）の結合テスト。
// seed の対応関係:
//   - general  (id=1): keisuke / haruka / ryo / mika / kenta 全員メンバー
//   - dev-backend (id=3): keisuke / haruka / ryo のみ。mika は非メンバー
//   - DM room 1 (ws=1): keisuke ⇔ haruka。mika は非メンバー
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class MessageThreadControllerIT {

    private static final long CH_GENERAL = 1L;
    private static final long CH_DEV_BACKEND = 3L;
    private static final long DM_ROOM = 1L;

    private static final String OWNER = "keisuke";
    private static final String MEMBER = "haruka";
    private static final String NON_MEMBER = "mika"; // dev-backend / DM room 1 の非メンバー
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

    // ============================================================
    // POST /api/messages/{parentId}/replies
    // ============================================================

    @Test
    void replyToChannelMessageHappyPath() throws Exception {
        Long parentId = createChannelMessage(OWNER, CH_GENERAL, "スレッドの親メッセージ");
        String token = login(MEMBER);

        mockMvc.perform(post("/api/messages/" + parentId + "/replies")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"スレッド返信です\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.parentMessageId").value(parentId))
                .andExpect(jsonPath("$.channelId").value(CH_GENERAL))
                .andExpect(jsonPath("$.body").value("スレッド返信です"));
    }

    @Test
    void replyToDmMessageHappyPath() throws Exception {
        Long parentId = createDmMessage(OWNER, DM_ROOM, "DM スレッドの親");
        String token = login(MEMBER); // haruka は DM room 1 のメンバー

        mockMvc.perform(post("/api/messages/" + parentId + "/replies")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"DM スレッド返信\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parentMessageId").value(parentId))
                .andExpect(jsonPath("$.dmRoomId").value(DM_ROOM))
                .andExpect(jsonPath("$.channelId").isEmpty());
    }

    @Test
    void replyToReplyRepointsToRoot() throws Exception {
        Long rootId = createChannelMessage(OWNER, CH_GENERAL, "スレッドの根");
        String token = login(MEMBER);

        // 1 段目の返信
        MvcResult firstReply = mockMvc.perform(post("/api/messages/" + rootId + "/replies")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"1段目の返信\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        Long firstReplyId = objectMapper.readTree(firstReply.getResponse().getContentAsString())
                .get("id").asLong();

        // 返信への返信 → root に付け替わる（Slack セマンティクス、スレッドは 1 階層）
        mockMvc.perform(post("/api/messages/" + firstReplyId + "/replies")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"返信への返信\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parentMessageId").value(rootId));
    }

    @Test
    void replyByNonMemberReturns403() throws Exception {
        Long parentId = createChannelMessage(OWNER, CH_DEV_BACKEND, "dev-backend の親メッセージ");
        String token = login(NON_MEMBER); // mika は dev-backend の非メンバー

        mockMvc.perform(post("/api/messages/" + parentId + "/replies")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"非メンバーの返信\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void replyToDeletedParentReturns404() throws Exception {
        Long parentId = createChannelMessage(OWNER, CH_GENERAL, "削除される親");
        Message parent = messageRepository.findById(parentId).orElseThrow();
        parent.setDeletedAt(java.time.OffsetDateTime.now());
        messageRepository.saveAndFlush(parent);

        String token = login(OWNER);
        mockMvc.perform(post("/api/messages/" + parentId + "/replies")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"削除済み親への返信\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void replyWithBlankBodyReturns422() throws Exception {
        Long parentId = createChannelMessage(OWNER, CH_GENERAL, "バリデーション用の親");
        String token = login(OWNER);

        mockMvc.perform(post("/api/messages/" + parentId + "/replies")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.title").value("Validation Failed"));
    }

    @Test
    void replyWithoutTokenReturns401() throws Exception {
        Long parentId = createChannelMessage(OWNER, CH_GENERAL, "トークンなし用の親");

        mockMvc.perform(post("/api/messages/" + parentId + "/replies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"トークンなし\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ============================================================
    // GET /api/messages/{parentId}/replies
    // ============================================================

    @Test
    void listRepliesInAscendingOrder() throws Exception {
        Long parentId = createChannelMessage(OWNER, CH_GENERAL, "一覧テストの親");
        String token = login(OWNER);

        Long first = createReplyViaApi(token, parentId, "1番目");
        Long second = createReplyViaApi(token, parentId, "2番目");

        MvcResult result = mockMvc.perform(get("/api/messages/" + parentId + "/replies")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andReturn();

        JsonNode items = objectMapper.readTree(result.getResponse().getContentAsString()).get("items");
        // 古い順（createdAt 昇順 = id 昇順）で返る
        assert items.get(0).get("id").asLong() == first;
        assert items.get(1).get("id").asLong() == second;
    }

    @Test
    void repliesAreExcludedFromChannelTimeline() throws Exception {
        Long parentId = createChannelMessage(OWNER, CH_GENERAL, "本流に出る親");
        String token = login(OWNER);
        Long replyId = createReplyViaApi(token, parentId, "本流には出ない返信");

        // チャンネル本流一覧には返信（parent あり）が含まれない
        mockMvc.perform(get("/api/channels/" + CH_GENERAL + "/messages")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id == " + replyId + ")]").doesNotExist())
                .andExpect(jsonPath("$.items[?(@.id == " + parentId + ")]").exists());
    }

    @Test
    void listRepliesByNonMemberReturns403() throws Exception {
        Long parentId = createChannelMessage(OWNER, CH_DEV_BACKEND, "dev-backend の親");
        String token = login(NON_MEMBER);

        mockMvc.perform(get("/api/messages/" + parentId + "/replies")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void listRepliesWithoutTokenReturns401() throws Exception {
        Long parentId = createChannelMessage(OWNER, CH_GENERAL, "トークンなし一覧用の親");

        mockMvc.perform(get("/api/messages/" + parentId + "/replies"))
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

    private Long createReplyViaApi(String token, Long parentId, String body) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/messages/" + parentId + "/replies")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"" + body + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
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
