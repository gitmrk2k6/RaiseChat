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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// seed の対応関係:
//   - general    (id=1): keisuke / haruka / ryo / mika / kenta 全員メンバー
//   - dev-backend (id=3): keisuke / haruka / ryo のみ。mika は非メンバー
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class MentionControllerIT {

    private static final long CH_GENERAL = 1L;
    private static final long CH_DEV_BACKEND = 3L;

    private static final String OWNER = "keisuke";
    private static final String MEMBER = "haruka";
    private static final String OTHER_MEMBER = "ryo";
    private static final String PASSWORD = "password";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private MentionRepository mentionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ChannelRepository channelRepository;

    // ============================================================
    // 送信（返信）時のパース
    // ============================================================

    @Test
    void replyParsesMentions() throws Exception {
        Long parentId = createTestMessage(OWNER, CH_GENERAL, "親メッセージ");
        String token = login(OWNER);

        mockMvc.perform(post("/api/messages/" + parentId + "/replies")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"レビューお願いします @haruka\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mentionedUserIds").isArray())
                .andExpect(jsonPath("$.mentionedUserIds[0]").value(userId(MEMBER)))
                .andExpect(jsonPath("$.mentionedUserIds.length()").value(1));
    }

    // ============================================================
    // 編集時の再同期
    // ============================================================

    @Test
    void editAddsMultipleMentionsInBodyOrder() throws Exception {
        Long msgId = createTestMessage(OWNER, CH_GENERAL, "メンションなし");
        String token = login(OWNER);

        mockMvc.perform(patch("/api/messages/" + msgId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"@haruka と @ryo に共有\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mentionedUserIds.length()").value(2))
                .andExpect(jsonPath("$.mentionedUserIds[0]").value(userId(MEMBER)))
                .andExpect(jsonPath("$.mentionedUserIds[1]").value(userId(OTHER_MEMBER)));
    }

    @Test
    void editResyncReplacesMentions() throws Exception {
        Long msgId = createTestMessage(OWNER, CH_GENERAL, "初期本文");
        String token = login(OWNER);

        // まず @haruka を含める
        mockMvc.perform(patch("/api/messages/" + msgId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"@haruka 確認して\"}"))
                .andExpect(status().isOk());

        // @ryo に置き換える → 古い @haruka は消える
        mockMvc.perform(patch("/api/messages/" + msgId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"やっぱり @ryo お願い\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mentionedUserIds.length()").value(1))
                .andExpect(jsonPath("$.mentionedUserIds[0]").value(userId(OTHER_MEMBER)));

        // DB 上も @ryo の 1 件のみ
        var rows = mentionRepository.findMentionedUserIdsByMessageId(msgId);
        assert rows.size() == 1 && rows.get(0).equals(userId(OTHER_MEMBER));
    }

    @Test
    void editClearingMentionRemovesIt() throws Exception {
        Long msgId = createTestMessage(OWNER, CH_GENERAL, "初期本文");
        String token = login(OWNER);

        mockMvc.perform(patch("/api/messages/" + msgId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"@haruka 見て\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mentionedUserIds.length()").value(1));

        mockMvc.perform(patch("/api/messages/" + msgId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"メンション消した\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mentionedUserIds.length()").value(0));
    }

    // ============================================================
    // 解決先の絞り込み（自分宛て / 非メンバー / 不明ハンドル）
    // ============================================================

    @Test
    void selfMentionIsExcluded() throws Exception {
        Long msgId = createTestMessage(OWNER, CH_GENERAL, "自分宛てテスト");
        String token = login(OWNER);

        mockMvc.perform(patch("/api/messages/" + msgId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"自分に @keisuke\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mentionedUserIds.length()").value(0));
    }

    @Test
    void nonMemberMentionIsExcluded() throws Exception {
        // dev-backend は mika 非メンバー。keisuke はメンバー。
        Long msgId = createTestMessage(OWNER, CH_DEV_BACKEND, "非メンバーテスト");
        String token = login(OWNER);

        mockMvc.perform(patch("/api/messages/" + msgId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"@mika は非メンバー、@haruka はメンバー\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mentionedUserIds.length()").value(1))
                .andExpect(jsonPath("$.mentionedUserIds[0]").value(userId(MEMBER)));
    }

    @Test
    void unknownHandleIsIgnored() throws Exception {
        Long msgId = createTestMessage(OWNER, CH_GENERAL, "不明ハンドルテスト");
        String token = login(OWNER);

        mockMvc.perform(patch("/api/messages/" + msgId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"存在しない @nobody_xyz さん\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mentionedUserIds.length()").value(0));
    }

    @Test
    void emailLikeTextIsNotParsedAsMention() throws Exception {
        // 直前が英数のため @ は無視される（user@example のようなケース）
        Long msgId = createTestMessage(OWNER, CH_GENERAL, "メールテスト");
        String token = login(OWNER);

        mockMvc.perform(patch("/api/messages/" + msgId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"連絡先は user@haruka です\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mentionedUserIds.length()").value(0));
    }

    // ============================================================
    // リスト取得での露出
    // ============================================================

    @Test
    void listExposesMentionedUserIds() throws Exception {
        Long msgId = createTestMessage(OWNER, CH_GENERAL, "リスト露出テスト");
        String token = login(OWNER);

        mockMvc.perform(patch("/api/messages/" + msgId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"@haruka を含む\"}"))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(get("/api/channels/" + CH_GENERAL + "/messages?limit=200")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode items = objectMapper.readTree(result.getResponse().getContentAsString()).get("items");
        boolean found = false;
        for (JsonNode item : items) {
            if (item.get("id").asLong() == msgId) {
                found = true;
                JsonNode mentions = item.get("mentionedUserIds");
                assert mentions.isArray() && mentions.size() == 1
                        && mentions.get(0).asLong() == userId(MEMBER);
            }
        }
        assert found : "編集したメッセージが list に見つからない";
    }

    // ============================================================
    // helpers
    // ============================================================

    private long userId(String handle) {
        return userRepository.findByUserId(handle).orElseThrow().getId();
    }

    private Long createTestMessage(String userHandle, Long channelId, String body) {
        User user = userRepository.findByUserId(userHandle).orElseThrow();
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
