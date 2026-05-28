package com.raisechat.channel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class ChannelControllerIT {

    // seed.sql の対応関係:
    //   - keisuke: ws-1 OWNER / ws-2 OWNER。channel: general / random / dev-backend / secret-pj / design 全部のメンバー
    //   - haruka:  ws-1 MEMBER。channel: general / random / dev-backend のメンバー
    //   - mika:    ws-1 MEMBER。channel: general / random / design のメンバー
    // seed の channel id 採番:
    //   1: general, 2: random, 3: dev-backend, 4: secret-pj (PRIVATE), 5: design
    private static final long CH_GENERAL = 1L;
    private static final long CH_RANDOM = 2L;
    private static final long CH_DEV_BACKEND = 3L;
    private static final long CH_SECRET_PJ = 4L;
    private static final long CH_DESIGN = 5L;

    private static final long WS_RAISETECH = 1L;
    private static final long WS_SIDE_PROJECT = 2L;

    private static final String OWNER = "keisuke";
    private static final String MEMBER = "haruka";
    private static final String NON_DEV_MEMBER = "mika";
    private static final String PASSWORD = "password";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // ============================================================
    // POST /api/workspaces/{wsId}/channels
    // ============================================================

    @Test
    void createHappyPath() throws Exception {
        String token = login(OWNER);

        String body = """
                {"name":"new-ch","description":"hello","type":"PUBLIC"}
                """;
        mockMvc.perform(post("/api/workspaces/" + WS_RAISETECH + "/channels")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.workspaceId").value(WS_RAISETECH))
                .andExpect(jsonPath("$.name").value("new-ch"))
                .andExpect(jsonPath("$.description").value("hello"))
                .andExpect(jsonPath("$.type").value("PUBLIC"))
                .andExpect(jsonPath("$.createdByUserId").isNumber())
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    @Test
    void createCreatorBecomesMember() throws Exception {
        String token = login(OWNER);

        String body = """
                {"name":"creator-test","type":"PUBLIC"}
                """;
        MvcResult result = mockMvc.perform(post("/api/workspaces/" + WS_RAISETECH + "/channels")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        long newId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();

        // joined=true で取得すると作成者は自分のチャンネルに含まれる
        mockMvc.perform(get("/api/workspaces/" + WS_RAISETECH + "/channels?joined=true")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id == " + newId + ")]").exists());
    }

    @Test
    void createWithoutTokenReturns401() throws Exception {
        mockMvc.perform(post("/api/workspaces/" + WS_RAISETECH + "/channels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\",\"type\":\"PUBLIC\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createBlankNameReturns422() throws Exception {
        String token = login(OWNER);

        mockMvc.perform(post("/api/workspaces/" + WS_RAISETECH + "/channels")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"type\":\"PUBLIC\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.title").value("Validation Failed"));
    }

    @Test
    void createWithoutTypeReturns422() throws Exception {
        String token = login(OWNER);

        mockMvc.perform(post("/api/workspaces/" + WS_RAISETECH + "/channels")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void createDuplicateNameReturns409() throws Exception {
        String token = login(OWNER);

        // seed で "general" が既存
        mockMvc.perform(post("/api/workspaces/" + WS_RAISETECH + "/channels")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"general\",\"type\":\"PUBLIC\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("Conflict"));
    }

    @Test
    void createByNonMemberReturns403() throws Exception {
        // haruka は ws-2 (Side Project) には未参加
        String token = login(MEMBER);

        mockMvc.perform(post("/api/workspaces/" + WS_SIDE_PROJECT + "/channels")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\",\"type\":\"PUBLIC\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void createForUnknownWorkspaceReturns404() throws Exception {
        String token = login(OWNER);

        mockMvc.perform(post("/api/workspaces/999999/channels")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\",\"type\":\"PUBLIC\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    // ============================================================
    // GET /api/workspaces/{wsId}/channels
    // ============================================================

    @Test
    void listForOwnerSeesAllChannelsIncludingPrivate() throws Exception {
        String token = login(OWNER);

        mockMvc.perform(get("/api/workspaces/" + WS_RAISETECH + "/channels")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(5))
                .andExpect(jsonPath("$.hasMore").value(false));
    }

    @Test
    void listForHidesPrivateNonMember() throws Exception {
        // haruka は secret-pj (PRIVATE) のメンバーではない → secret-pj は見えない
        String token = login(MEMBER);

        mockMvc.perform(get("/api/workspaces/" + WS_RAISETECH + "/channels")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id == " + CH_SECRET_PJ + ")]").doesNotExist())
                .andExpect(jsonPath("$.items[?(@.id == " + CH_GENERAL + ")]").exists());
    }

    @Test
    void listFilterByType() throws Exception {
        String token = login(OWNER);

        mockMvc.perform(get("/api/workspaces/" + WS_RAISETECH + "/channels?type=PRIVATE")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(CH_SECRET_PJ));
    }

    @Test
    void listFilterByJoinedTrue() throws Exception {
        // mika は general / random / design のみ参加（dev-backend は非メンバー）
        String token = login(NON_DEV_MEMBER);

        mockMvc.perform(get("/api/workspaces/" + WS_RAISETECH + "/channels?joined=true")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[?(@.id == " + CH_DEV_BACKEND + ")]").doesNotExist())
                .andExpect(jsonPath("$.items[?(@.id == " + CH_DESIGN + ")]").exists());
    }

    @Test
    void listForNonMemberReturns403() throws Exception {
        String token = login(MEMBER);

        mockMvc.perform(get("/api/workspaces/" + WS_SIDE_PROJECT + "/channels")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void listWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/workspaces/" + WS_RAISETECH + "/channels"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listPagesWithCursor() throws Exception {
        String token = login(OWNER);

        // limit=2: 1ページ目 → id 1,2 で hasMore=true / nextCursor="2"
        mockMvc.perform(get("/api/workspaces/" + WS_RAISETECH + "/channels?limit=2")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].id").value(CH_GENERAL))
                .andExpect(jsonPath("$.items[1].id").value(CH_RANDOM))
                .andExpect(jsonPath("$.hasMore").value(true))
                .andExpect(jsonPath("$.nextCursor").value("2"));
    }

    // ============================================================
    // GET /api/channels/{id}
    // ============================================================

    @Test
    void getDetailPublicChannelForWorkspaceMember() throws Exception {
        String token = login(MEMBER);

        mockMvc.perform(get("/api/channels/" + CH_GENERAL)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(CH_GENERAL))
                .andExpect(jsonPath("$.name").value("general"))
                .andExpect(jsonPath("$.type").value("PUBLIC"));
    }

    @Test
    void getDetailPrivateChannelForChannelMember() throws Exception {
        String token = login(OWNER);

        mockMvc.perform(get("/api/channels/" + CH_SECRET_PJ)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(CH_SECRET_PJ))
                .andExpect(jsonPath("$.type").value("PRIVATE"));
    }

    @Test
    void getDetailPrivateChannelForNonChannelMemberReturns403() throws Exception {
        // haruka は ws-1 メンバーだが secret-pj のメンバーではない
        String token = login(MEMBER);

        mockMvc.perform(get("/api/channels/" + CH_SECRET_PJ)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void getDetailUnknownIdReturns404() throws Exception {
        String token = login(OWNER);

        mockMvc.perform(get("/api/channels/999999")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    // ============================================================
    // POST /api/channels/{id}/join
    // ============================================================

    @Test
    void joinPublicChannelHappyPath() throws Exception {
        // mika は design メンバーだが dev-backend には未参加 → join 可能
        String token = login(NON_DEV_MEMBER);

        mockMvc.perform(post("/api/channels/" + CH_DEV_BACKEND + "/join")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(CH_DEV_BACKEND));
    }

    @Test
    void joinPrivateChannelReturns403() throws Exception {
        // haruka は secret-pj (PRIVATE) に未参加 → 招待なしの join は禁止
        String token = login(MEMBER);

        mockMvc.perform(post("/api/channels/" + CH_SECRET_PJ + "/join")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void joinWhenAlreadyMemberReturns409() throws Exception {
        // haruka は general のメンバー → 再 join は 409
        String token = login(MEMBER);

        mockMvc.perform(post("/api/channels/" + CH_GENERAL + "/join")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());
    }

    // ============================================================
    // POST /api/channels/{id}/leave
    // ============================================================

    @Test
    void leaveHappyPath() throws Exception {
        // haruka は random のメンバー → leave 可
        String token = login(MEMBER);

        mockMvc.perform(post("/api/channels/" + CH_RANDOM + "/leave")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    void leaveGeneralChannelReturns409() throws Exception {
        String token = login(MEMBER);

        mockMvc.perform(post("/api/channels/" + CH_GENERAL + "/leave")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());
    }

    @Test
    void leaveWhenNotMemberReturns409() throws Exception {
        // mika は dev-backend のメンバーではない → leave しようとすると 409
        String token = login(NON_DEV_MEMBER);

        mockMvc.perform(post("/api/channels/" + CH_DEV_BACKEND + "/leave")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());
    }

    // ============================================================
    // DELETE /api/channels/{id}
    // ============================================================

    @Test
    void deleteByOwnerHappyPath() throws Exception {
        String token = login(OWNER);

        mockMvc.perform(delete("/api/channels/" + CH_RANDOM)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        // 論理削除されたので 404 が返る
        mockMvc.perform(get("/api/channels/" + CH_RANDOM)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteByNonOwnerNonCreatorReturns403() throws Exception {
        // haruka は ws-1 MEMBER で random の作成者でもない → 403
        String token = login(MEMBER);

        mockMvc.perform(delete("/api/channels/" + CH_RANDOM)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteGeneralChannelReturns409() throws Exception {
        String token = login(OWNER);

        mockMvc.perform(delete("/api/channels/" + CH_GENERAL)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());
    }

    @Test
    void deleteUnknownIdReturns404() throws Exception {
        String token = login(OWNER);

        mockMvc.perform(delete("/api/channels/999999")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    // ============================================================
    // workspace 作成時の general 自動作成（F-03 で先送り分）
    // ============================================================

    @Test
    void createWorkspaceAutoCreatesGeneralChannel() throws Exception {
        String token = login(OWNER);

        MvcResult result = mockMvc.perform(post("/api/workspaces")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Auto General WS\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        long newWsId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();

        // 新規 ws のチャンネル一覧に general が含まれている（PUBLIC、作成者がメンバー）
        mockMvc.perform(get("/api/workspaces/" + newWsId + "/channels")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].name").value("general"))
                .andExpect(jsonPath("$.items[0].type").value("PUBLIC"));
    }

    // ============================================================
    // helpers
    // ============================================================

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
