package com.raisechat.workspace;

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

import java.time.OffsetDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class WorkspaceInviteControllerIT {

    // seed: keisuke=ws-1 OWNER & ws-2 OWNER, haruka=ws-1 MEMBER（ws-2 は未参加）
    private static final String OWNER_USER = "keisuke";
    private static final String MEMBER_USER = "haruka";
    private static final String OTHER_USER = "ryo";
    private static final String PASSWORD = "password";

    private static final long WS_WITH_ONLY_OWNER = 2L; // Side Project（keisuke のみ）
    private static final long WS_SHARED = 1L;           // RaiseTech AI（keisuke=OWNER, haruka=MEMBER）

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WorkspaceInviteRepository workspaceInviteRepository;

    // ---------- POST /api/workspaces/{wsId}/invites（発行） ----------

    @Test
    void createInviteByOwnerReturns201WithToken() throws Exception {
        String token = login(OWNER_USER);

        mockMvc.perform(post("/api/workspaces/" + WS_WITH_ONLY_OWNER + "/invites")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expiresInHours\":24,\"maxUses\":5}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.workspaceId").value((int) WS_WITH_ONLY_OWNER))
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.inviteUrl").isString())
                .andExpect(jsonPath("$.maxUses").value(5))
                .andExpect(jsonPath("$.usedCount").value(0))
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    @Test
    void createInviteWithEmptyBodyUsesDefaults() throws Exception {
        String token = login(OWNER_USER);

        mockMvc.perform(post("/api/workspaces/" + WS_WITH_ONLY_OWNER + "/invites")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.maxUses").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void createInviteByNonOwnerMemberReturns403() throws Exception {
        // haruka は ws-1 の MEMBER（非 OWNER）
        String token = login(MEMBER_USER);

        mockMvc.perform(post("/api/workspaces/" + WS_SHARED + "/invites")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Forbidden"));
    }

    @Test
    void createInviteByNonMemberReturns403() throws Exception {
        // haruka は ws-2 に未参加
        String token = login(MEMBER_USER);

        mockMvc.perform(post("/api/workspaces/" + WS_WITH_ONLY_OWNER + "/invites")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void createInviteForUnknownWorkspaceReturns404() throws Exception {
        String token = login(OWNER_USER);

        mockMvc.perform(post("/api/workspaces/999999/invites")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void createInviteWithoutTokenReturns401() throws Exception {
        mockMvc.perform(post("/api/workspaces/" + WS_WITH_ONLY_OWNER + "/invites")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createInviteWithInvalidMaxUsesReturns422() throws Exception {
        String token = login(OWNER_USER);

        mockMvc.perform(post("/api/workspaces/" + WS_WITH_ONLY_OWNER + "/invites")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"maxUses\":0}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("maxUses"));
    }

    // ---------- POST /api/invites/{token}/accept（受諾） ----------

    @Test
    void acceptInviteAddsUserAsMemberAndJoinsGeneral() throws Exception {
        String ownerToken = login(OWNER_USER);
        // API で新規 WS を作成すると general チャンネルが自動生成される（seed の ws-2 はチャンネル無し）。
        long newWsId = createWorkspace(ownerToken, "Invite Target WS");
        String rawToken = issueInvite(ownerToken, newWsId, "{}");

        // haruka は新規 WS 未参加 → 受諾で参加
        String harukaToken = login(MEMBER_USER);
        mockMvc.perform(post("/api/invites/" + rawToken + "/accept")
                        .header("Authorization", "Bearer " + harukaToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value((int) newWsId));

        // ワークスペース詳細にメンバーとして現れる
        mockMvc.perform(get("/api/workspaces/" + newWsId)
                        .header("Authorization", "Bearer " + harukaToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members[?(@.userId=='" + MEMBER_USER + "')]").exists());

        // general チャンネルにも参加済み（joined=true で一覧に general が出る）
        mockMvc.perform(get("/api/workspaces/" + newWsId + "/channels?joined=true")
                        .header("Authorization", "Bearer " + harukaToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.name=='general')]").exists());
    }

    @Test
    void acceptInviteByExistingMemberIsIdempotent() throws Exception {
        String ownerToken = login(OWNER_USER);
        // keisuke は ws-2 の OWNER（＝既にメンバー）。自分の招待を受諾しても 200。
        String rawToken = issueInvite(ownerToken, WS_WITH_ONLY_OWNER, "{}");

        mockMvc.perform(post("/api/invites/" + rawToken + "/accept")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value((int) WS_WITH_ONLY_OWNER));

        // used_count は消費されない
        long inviteId = latestInviteId(ownerToken, WS_WITH_ONLY_OWNER, rawToken);
        org.junit.jupiter.api.Assertions.assertEquals(
                0, workspaceInviteRepository.findById(inviteId).orElseThrow().getUsedCount());
    }

    @Test
    void acceptUnknownTokenReturns404() throws Exception {
        String token = login(MEMBER_USER);

        mockMvc.perform(post("/api/invites/this-token-does-not-exist/accept")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void acceptRevokedInviteReturns410() throws Exception {
        String ownerToken = login(OWNER_USER);
        String rawToken = issueInvite(ownerToken, WS_WITH_ONLY_OWNER, "{}");
        long inviteId = latestInviteId(ownerToken, WS_WITH_ONLY_OWNER, rawToken);

        // 無効化
        mockMvc.perform(delete("/api/workspaces/" + WS_WITH_ONLY_OWNER + "/invites/" + inviteId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());

        String harukaToken = login(MEMBER_USER);
        mockMvc.perform(post("/api/invites/" + rawToken + "/accept")
                        .header("Authorization", "Bearer " + harukaToken))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.title").value("Gone"));
    }

    @Test
    void acceptExpiredInviteReturns410() throws Exception {
        String ownerToken = login(OWNER_USER);
        String rawToken = issueInvite(ownerToken, WS_WITH_ONLY_OWNER, "{}");
        long inviteId = latestInviteId(ownerToken, WS_WITH_ONLY_OWNER, rawToken);

        // 期限を過去に書き換え（同一テストトランザクション内なので可視）
        WorkspaceInvite invite = workspaceInviteRepository.findById(inviteId).orElseThrow();
        invite.setExpiresAt(OffsetDateTime.now().minusHours(1));
        workspaceInviteRepository.saveAndFlush(invite);

        String harukaToken = login(MEMBER_USER);
        mockMvc.perform(post("/api/invites/" + rawToken + "/accept")
                        .header("Authorization", "Bearer " + harukaToken))
                .andExpect(status().isGone());
    }

    @Test
    void acceptAfterMaxUsesExhaustedReturns410() throws Exception {
        String ownerToken = login(OWNER_USER);
        String rawToken = issueInvite(ownerToken, WS_WITH_ONLY_OWNER, "{\"maxUses\":1}");

        // 1 人目（haruka）受諾 → 上限到達
        mockMvc.perform(post("/api/invites/" + rawToken + "/accept")
                        .header("Authorization", "Bearer " + login(MEMBER_USER)))
                .andExpect(status().isOk());

        // 2 人目（ryo）受諾 → 410
        mockMvc.perform(post("/api/invites/" + rawToken + "/accept")
                        .header("Authorization", "Bearer " + login(OTHER_USER)))
                .andExpect(status().isGone());
    }

    @Test
    void acceptWithoutTokenReturns401() throws Exception {
        mockMvc.perform(post("/api/invites/whatever/accept"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- DELETE /api/workspaces/{wsId}/invites/{inviteId}（無効化） ----------

    @Test
    void revokeByOwnerReturns204AndBlocksAccept() throws Exception {
        String ownerToken = login(OWNER_USER);
        String rawToken = issueInvite(ownerToken, WS_WITH_ONLY_OWNER, "{}");
        long inviteId = latestInviteId(ownerToken, WS_WITH_ONLY_OWNER, rawToken);

        mockMvc.perform(delete("/api/workspaces/" + WS_WITH_ONLY_OWNER + "/invites/" + inviteId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/invites/" + rawToken + "/accept")
                        .header("Authorization", "Bearer " + login(MEMBER_USER)))
                .andExpect(status().isGone());
    }

    @Test
    void revokeByNonOwnerReturns403() throws Exception {
        String ownerToken = login(OWNER_USER);
        String rawToken = issueInvite(ownerToken, WS_SHARED, "{}");
        long inviteId = latestInviteId(ownerToken, WS_SHARED, rawToken);

        // haruka は ws-1 の MEMBER（非 OWNER）
        mockMvc.perform(delete("/api/workspaces/" + WS_SHARED + "/invites/" + inviteId)
                        .header("Authorization", "Bearer " + login(MEMBER_USER)))
                .andExpect(status().isForbidden());
    }

    @Test
    void revokeInviteOfAnotherWorkspaceReturns404() throws Exception {
        String ownerToken = login(OWNER_USER);
        String rawToken = issueInvite(ownerToken, WS_WITH_ONLY_OWNER, "{}");
        long inviteId = latestInviteId(ownerToken, WS_WITH_ONLY_OWNER, rawToken);

        // ws-2 の招待を ws-1 のパスで無効化しようとする → findByIdAndWorkspaceId 不一致で 404
        mockMvc.perform(delete("/api/workspaces/" + WS_SHARED + "/invites/" + inviteId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNotFound());
    }

    // ---------- helpers ----------

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

    // ワークスペースを新規作成して ID を返す（general チャンネルが自動生成される）。
    private long createWorkspace(String ownerToken, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/workspaces")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"%s\"}".formatted(name)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    // 招待を発行して平文トークンを返す。
    private String issueInvite(String ownerToken, long wsId, String requestBody) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/workspaces/" + wsId + "/invites")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    // 発行レスポンスから招待 ID を取り出す（同一発行を再呼び出しせず、上で得た rawToken に対応する ID を返す）。
    private long latestInviteId(String ownerToken, long wsId, String rawToken) throws Exception {
        // rawToken に対応する ID は発行レスポンスにのみ含まれるため、issueInvite 時の ID を使い回せるよう
        // ここでは token_hash 経由でリポジトリから引く（テスト専用の簡便策）。
        return workspaceInviteRepository.findByTokenHash(new InviteTokenService().hash(rawToken))
                .orElseThrow().getId();
    }
}
