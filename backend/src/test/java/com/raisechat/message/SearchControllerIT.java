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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// seed（R__seed_dev.sql）の前提:
//   - ワークスペース RaiseTech AI (id=1): keisuke / haruka / ryo / mika / kenta
//   - dev-backend チャンネル: ryo の "Spring Boot 3.x の WebSocket 設定..." 等。mika は非メンバー
//   - DM keisuke⇔ryo: keisuke の "PR #5 出しました..."
//   - Side Project (id=2): keisuke のみ。mika は非メンバー
// 全文検索は to_tsvector('simple', body) のため、英数字トークン（Spring / WebSocket / PR 等）で検証する。
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class SearchControllerIT {

    private static final long WS_RAISETECH = 1L;
    private static final long WS_SIDE_PROJECT = 2L;
    private static final long CH_GENERAL = 1L; // keisuke はメンバー

    private static final String OWNER = "keisuke";   // dev-backend / secret-pj メンバー
    private static final String MIKA = "mika";       // dev-backend / secret-pj 非メンバー
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

    @Test
    void searchHappyPath() throws Exception {
        String token = login(OWNER);

        mockMvc.perform(get("/api/workspaces/" + WS_RAISETECH + "/search")
                        .param("q", "Spring")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items[0].messageId").isNumber())
                .andExpect(jsonPath("$.items[0].body").isString())
                .andExpect(jsonPath("$.items[0].authorDisplayName").isString())
                .andExpect(jsonPath("$.items[0].channelName").value("dev-backend"));
    }

    @Test
    void searchMatchesDmMessages() throws Exception {
        // keisuke⇔ryo の DM "PR #5 出しました..." がヒットする。channelName は null（DM）。
        String token = login(OWNER);

        mockMvc.perform(get("/api/workspaces/" + WS_RAISETECH + "/search")
                        .param("q", "PR")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.dmRoomId != null)]").exists());
    }

    @Test
    void searchExcludesNonMemberChannel() throws Exception {
        // "WebSocket" は dev-backend のメッセージにのみ含まれる。mika は dev-backend 非メンバーなので
        // 結果は空になる（searchByMemberFindsMemberChannel での keisuke の結果と対比）。
        String token = login(MIKA);

        mockMvc.perform(get("/api/workspaces/" + WS_RAISETECH + "/search")
                        .param("q", "WebSocket")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void searchByMemberFindsMemberChannel() throws Exception {
        // keisuke は dev-backend メンバーなので "WebSocket" がヒットする（非メンバー mika との対比）。
        String token = login(OWNER);

        mockMvc.perform(get("/api/workspaces/" + WS_RAISETECH + "/search")
                        .param("q", "WebSocket")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].channelName").value("dev-backend"));
    }

    @Test
    void searchNoMatchReturnsEmpty() throws Exception {
        String token = login(OWNER);

        mockMvc.perform(get("/api/workspaces/" + WS_RAISETECH + "/search")
                        .param("q", "zzzznomatchzzzz")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.hasMore").value(false))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
    }

    @Test
    void searchWithCursorPagination() throws Exception {
        // ユニークなトークンを含むメッセージを general（keisuke がメンバー）に 2 件作成し、
        // limit=1 で id DESC・カーソルページングが機能することを確認する（@Transactional でロールバック）。
        String token = "Zephyrium" + System.nanoTime();
        Long first = createMessage(OWNER, CH_GENERAL, "1件目 " + token);
        Long second = createMessage(OWNER, CH_GENERAL, "2件目 " + token);

        String jwt = login(OWNER);

        // 1ページ目（新着順なので second が先頭、hasMore=true、nextCursor=second.id）
        MvcResult firstPage = mockMvc.perform(get("/api/workspaces/" + WS_RAISETECH + "/search")
                        .param("q", token)
                        .param("limit", "1")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].messageId").value(second))
                .andExpect(jsonPath("$.hasMore").value(true))
                .andExpect(jsonPath("$.nextCursor").value(String.valueOf(second)))
                .andReturn();

        String nextCursor = objectMapper.readTree(firstPage.getResponse().getContentAsString())
                .get("nextCursor").asText();

        // 2ページ目（cursor より古い first が返り、hasMore=false）
        mockMvc.perform(get("/api/workspaces/" + WS_RAISETECH + "/search")
                        .param("q", token)
                        .param("limit", "1")
                        .param("cursor", nextCursor)
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].messageId").value(first))
                .andExpect(jsonPath("$.hasMore").value(false));
    }

    @Test
    void searchBlankQueryReturns422() throws Exception {
        String token = login(OWNER);

        mockMvc.perform(get("/api/workspaces/" + WS_RAISETECH + "/search")
                        .param("q", "   ")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.title").value("Validation Failed"));
    }

    @Test
    void searchMissingQueryReturns422() throws Exception {
        String token = login(OWNER);

        mockMvc.perform(get("/api/workspaces/" + WS_RAISETECH + "/search")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void searchByNonWorkspaceMemberReturns403() throws Exception {
        // mika は Side Project (id=2) のメンバーではない。
        String token = login(MIKA);

        mockMvc.perform(get("/api/workspaces/" + WS_SIDE_PROJECT + "/search")
                        .param("q", "Spring")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void searchNonExistentWorkspaceReturns404() throws Exception {
        String token = login(OWNER);

        mockMvc.perform(get("/api/workspaces/99999999/search")
                        .param("q", "Spring")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void searchWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/workspaces/" + WS_RAISETECH + "/search")
                        .param("q", "Spring"))
                .andExpect(status().isUnauthorized());
    }

    // ============================================================
    // helpers
    // ============================================================

    private Long createMessage(String userId, Long channelId, String body) {
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
