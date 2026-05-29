package com.raisechat.message;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raisechat.channel.Channel;
import com.raisechat.channel.ChannelRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// seed の対応関係:
//   - general  (id=1): keisuke / haruka / ryo / mika / kenta 全員メンバー
//   - dev-backend (id=3): keisuke / haruka / ryo のみ。mika は非メンバー
//   - secret-pj (id=4, PRIVATE): keisuke / ryo のみ。haruka は非メンバー
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class MessageControllerIT {

    private static final long CH_GENERAL = 1L;
    private static final long CH_DEV_BACKEND = 3L;

    private static final String OWNER = "keisuke";
    private static final String MEMBER = "haruka";
    private static final String NON_MEMBER = "mika"; // dev-backend の非メンバー
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

    // ============================================================
    // GET /api/channels/{id}/messages
    // ============================================================

    @Test
    void listHappyPath() throws Exception {
        String token = login(OWNER);

        mockMvc.perform(get("/api/channels/" + CH_GENERAL + "/messages")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items[0].id").isNumber())
                .andExpect(jsonPath("$.items[0].channelId").value(CH_GENERAL))
                .andExpect(jsonPath("$.items[0].authorDisplayName").isString())
                .andExpect(jsonPath("$.items[0].body").isString())
                .andExpect(jsonPath("$.items[0].createdAt").isNotEmpty());
    }

    @Test
    void listExcludesThreadReplies() throws Exception {
        String token = login(OWNER);

        MvcResult result = mockMvc.perform(get("/api/channels/" + CH_GENERAL + "/messages")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode items = objectMapper.readTree(result.getResponse().getContentAsString()).get("items");
        // seed の general には parent_message_id が設定されているスレッド返信もあるが
        // list には含まれない（parent IS NULL のみ返す）
        for (JsonNode item : items) {
            assert item.get("parentMessageId").isNull() || item.get("parentMessageId") == null;
        }
    }

    @Test
    void listWithCursorPagination() throws Exception {
        String token = login(OWNER);

        // limit=2 で最初のページ取得
        MvcResult firstPage = mockMvc.perform(get("/api/channels/" + CH_GENERAL + "/messages?limit=2")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.hasMore").value(true))
                .andExpect(jsonPath("$.nextCursor").isString())
                .andReturn();

        String nextCursor = objectMapper.readTree(firstPage.getResponse().getContentAsString())
                .get("nextCursor").asText();

        // cursor を使って次ページ取得 → さらに古いメッセージが返る
        mockMvc.perform(get("/api/channels/" + CH_GENERAL + "/messages?limit=2&cursor=" + nextCursor)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());
    }

    @Test
    void listByNonMemberReturns403() throws Exception {
        // mika は dev-backend の非メンバー
        String token = login(NON_MEMBER);

        mockMvc.perform(get("/api/channels/" + CH_DEV_BACKEND + "/messages")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void listWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/channels/" + CH_GENERAL + "/messages"))
                .andExpect(status().isUnauthorized());
    }

    // ============================================================
    // PATCH /api/messages/{id}
    // ============================================================

    @Test
    void editByAuthorHappyPath() throws Exception {
        Long msgId = createTestMessage(OWNER, CH_GENERAL, "編集前のメッセージ");
        String token = login(OWNER);

        mockMvc.perform(patch("/api/messages/" + msgId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"編集後のメッセージ\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(msgId))
                .andExpect(jsonPath("$.body").value("編集後のメッセージ"))
                .andExpect(jsonPath("$.editedAt").isNotEmpty());
    }

    @Test
    void editByNonAuthorReturns403() throws Exception {
        Long msgId = createTestMessage(OWNER, CH_GENERAL, "OWNER が投稿したメッセージ");
        String token = login(MEMBER); // haruka は author ではない

        mockMvc.perform(patch("/api/messages/" + msgId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"他人のメッセージを編集しようとする\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void editDeletedMessageReturns404() throws Exception {
        Long msgId = createTestMessage(OWNER, CH_GENERAL, "削除済みになるメッセージ");
        // 先に削除
        Message msg = messageRepository.findById(msgId).orElseThrow();
        msg.setDeletedAt(java.time.OffsetDateTime.now());
        messageRepository.saveAndFlush(msg);

        String token = login(OWNER);
        mockMvc.perform(patch("/api/messages/" + msgId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"削除済みを編集しようとする\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void editWithBlankBodyReturns422() throws Exception {
        Long msgId = createTestMessage(OWNER, CH_GENERAL, "バリデーションテスト用");
        String token = login(OWNER);

        mockMvc.perform(patch("/api/messages/" + msgId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.title").value("Validation Failed"));
    }

    @Test
    void editWithoutTokenReturns401() throws Exception {
        Long msgId = createTestMessage(OWNER, CH_GENERAL, "トークンなしテスト");

        mockMvc.perform(patch("/api/messages/" + msgId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"更新内容\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ============================================================
    // DELETE /api/messages/{id}
    // ============================================================

    @Test
    void deleteByAuthorHappyPath() throws Exception {
        Long msgId = createTestMessage(OWNER, CH_GENERAL, "削除対象メッセージ");
        String token = login(OWNER);

        mockMvc.perform(delete("/api/messages/" + msgId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        // 削除後はリストから除外される
        mockMvc.perform(get("/api/channels/" + CH_GENERAL + "/messages")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id == " + msgId + ")]").doesNotExist());
    }

    @Test
    void deleteByWorkspaceOwnerHappyPath() throws Exception {
        // haruka（MEMBER）が投稿したメッセージを keisuke（ws OWNER）が削除
        Long msgId = createTestMessage(MEMBER, CH_GENERAL, "MEMBER が投稿。OWNER が削除する");
        String ownerToken = login(OWNER);

        mockMvc.perform(delete("/api/messages/" + msgId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteByNonAuthorNonOwnerReturns403() throws Exception {
        Long msgId = createTestMessage(OWNER, CH_GENERAL, "OWNER が投稿したメッセージ");
        String memberToken = login(MEMBER); // haruka は author でも OWNER でもない

        mockMvc.perform(delete("/api/messages/" + msgId)
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteNonExistentReturns404() throws Exception {
        String token = login(OWNER);

        mockMvc.perform(delete("/api/messages/99999999")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteWithoutTokenReturns401() throws Exception {
        Long msgId = createTestMessage(OWNER, CH_GENERAL, "トークンなし削除テスト");

        mockMvc.perform(delete("/api/messages/" + msgId))
                .andExpect(status().isUnauthorized());
    }

    // ============================================================
    // helpers
    // ============================================================

    private Long createTestMessage(String userId, Long channelId, String body) {
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
