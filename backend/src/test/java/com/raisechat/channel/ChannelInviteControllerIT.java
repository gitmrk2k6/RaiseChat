package com.raisechat.channel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raisechat.workspace.InviteTokenService;
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
class ChannelInviteControllerIT {

    // seed（すべて ws-1=RaiseTech AI）:
    //   secret-pj(PRIVATE, id=4) メンバー: keisuke, ryo
    //   general(PUBLIC, id=1)    メンバー: keisuke, haruka, ryo, mika, kenta
    //   ws-1 メンバー: keisuke=OWNER, haruka/ryo/mika/kenta=MEMBER
    private static final String CHANNEL_MEMBER = "keisuke"; // secret-pj メンバー
    private static final String WS_MEMBER_NON_CHANNEL = "haruka"; // ws-1 メンバーだが secret-pj 非メンバー
    private static final String CHANNEL_MEMBER_2 = "ryo"; // secret-pj メンバー
    private static final String WS_MEMBER_NON_CHANNEL_2 = "mika"; // ws-1 メンバーだが secret-pj 非メンバー
    private static final String PASSWORD = "password";

    private static final long PRIVATE_CHANNEL_ID = 4L; // secret-pj
    private static final long GENERAL_CHANNEL_ID = 1L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ChannelInviteRepository channelInviteRepository;

    // ---------- POST /api/channels/{id}/invites（発行） ----------

    @Test
    void createInviteByChannelMemberReturns201WithToken() throws Exception {
        String token = login(CHANNEL_MEMBER);

        mockMvc.perform(post("/api/channels/" + PRIVATE_CHANNEL_ID + "/invites")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expiresInHours\":24,\"maxUses\":5}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.channelId").value((int) PRIVATE_CHANNEL_ID))
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.inviteUrl").isString())
                .andExpect(jsonPath("$.maxUses").value(5))
                .andExpect(jsonPath("$.usedCount").value(0))
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    @Test
    void createInviteWithEmptyBodyUsesDefaults() throws Exception {
        String token = login(CHANNEL_MEMBER);

        mockMvc.perform(post("/api/channels/" + PRIVATE_CHANNEL_ID + "/invites")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.maxUses").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void createInviteByNonChannelMemberReturns403() throws Exception {
        // haruka は ws-1 メンバーだが secret-pj の非メンバー
        String token = login(WS_MEMBER_NON_CHANNEL);

        mockMvc.perform(post("/api/channels/" + PRIVATE_CHANNEL_ID + "/invites")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Forbidden"));
    }

    @Test
    void createInviteForUnknownChannelReturns404() throws Exception {
        String token = login(CHANNEL_MEMBER);

        mockMvc.perform(post("/api/channels/999999/invites")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void createInviteWithoutTokenReturns401() throws Exception {
        mockMvc.perform(post("/api/channels/" + PRIVATE_CHANNEL_ID + "/invites")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createInviteWithInvalidMaxUsesReturns422() throws Exception {
        String token = login(CHANNEL_MEMBER);

        mockMvc.perform(post("/api/channels/" + PRIVATE_CHANNEL_ID + "/invites")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"maxUses\":0}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("maxUses"));
    }

    // ---------- POST /api/channel-invites/{token}/accept（受諾） ----------

    @Test
    void acceptInviteAddsUserAsChannelMember() throws Exception {
        String rawToken = issueInvite(login(CHANNEL_MEMBER), PRIVATE_CHANNEL_ID, "{}");

        // haruka は secret-pj 未参加 → 受諾で参加
        String harukaToken = login(WS_MEMBER_NON_CHANNEL);
        mockMvc.perform(post("/api/channel-invites/" + rawToken + "/accept")
                        .header("Authorization", "Bearer " + harukaToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value((int) PRIVATE_CHANNEL_ID));

        // メンバーになったので PRIVATE チャンネルの詳細を閲覧できる
        mockMvc.perform(get("/api/channels/" + PRIVATE_CHANNEL_ID)
                        .header("Authorization", "Bearer " + harukaToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value((int) PRIVATE_CHANNEL_ID));
    }

    @Test
    void acceptInviteByExistingMemberIsIdempotent() throws Exception {
        String rawToken = issueInvite(login(CHANNEL_MEMBER), PRIVATE_CHANNEL_ID, "{}");

        // ryo は secret-pj の既存メンバー。受諾しても 200 で used_count は消費されない。
        mockMvc.perform(post("/api/channel-invites/" + rawToken + "/accept")
                        .header("Authorization", "Bearer " + login(CHANNEL_MEMBER_2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value((int) PRIVATE_CHANNEL_ID));

        long inviteId = inviteIdOf(rawToken);
        org.junit.jupiter.api.Assertions.assertEquals(
                0, channelInviteRepository.findById(inviteId).orElseThrow().getUsedCount());
    }

    @Test
    void acceptByNonWorkspaceMemberReturns403() throws Exception {
        // keisuke が新規 WS を作成（keisuke のみメンバー）し、その中に PRIVATE チャンネルを作って招待を発行。
        String ownerToken = login(CHANNEL_MEMBER);
        long newWsId = createWorkspace(ownerToken, "Channel Invite WS");
        long newChannelId = createChannel(ownerToken, newWsId, "secret", "PRIVATE");
        String rawToken = issueInvite(ownerToken, newChannelId, "{}");

        // haruka は新規 WS の非メンバー → チャンネル招待を受諾できない（403）
        mockMvc.perform(post("/api/channel-invites/" + rawToken + "/accept")
                        .header("Authorization", "Bearer " + login(WS_MEMBER_NON_CHANNEL)))
                .andExpect(status().isForbidden());
    }

    @Test
    void acceptUnknownTokenReturns404() throws Exception {
        mockMvc.perform(post("/api/channel-invites/this-token-does-not-exist/accept")
                        .header("Authorization", "Bearer " + login(WS_MEMBER_NON_CHANNEL)))
                .andExpect(status().isNotFound());
    }

    @Test
    void acceptRevokedInviteReturns410() throws Exception {
        String ownerToken = login(CHANNEL_MEMBER);
        String rawToken = issueInvite(ownerToken, PRIVATE_CHANNEL_ID, "{}");
        long inviteId = inviteIdOf(rawToken);

        mockMvc.perform(delete("/api/channels/" + PRIVATE_CHANNEL_ID + "/invites/" + inviteId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/channel-invites/" + rawToken + "/accept")
                        .header("Authorization", "Bearer " + login(WS_MEMBER_NON_CHANNEL)))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.title").value("Gone"));
    }

    @Test
    void acceptExpiredInviteReturns410() throws Exception {
        String rawToken = issueInvite(login(CHANNEL_MEMBER), PRIVATE_CHANNEL_ID, "{}");
        long inviteId = inviteIdOf(rawToken);

        // 期限を過去に書き換え（同一テストトランザクション内なので可視）
        ChannelInvite invite = channelInviteRepository.findById(inviteId).orElseThrow();
        invite.setExpiresAt(OffsetDateTime.now().minusHours(1));
        channelInviteRepository.saveAndFlush(invite);

        mockMvc.perform(post("/api/channel-invites/" + rawToken + "/accept")
                        .header("Authorization", "Bearer " + login(WS_MEMBER_NON_CHANNEL)))
                .andExpect(status().isGone());
    }

    @Test
    void acceptAfterMaxUsesExhaustedReturns410() throws Exception {
        String rawToken = issueInvite(login(CHANNEL_MEMBER), PRIVATE_CHANNEL_ID, "{\"maxUses\":1}");

        // 1 人目（haruka）受諾 → 上限到達
        mockMvc.perform(post("/api/channel-invites/" + rawToken + "/accept")
                        .header("Authorization", "Bearer " + login(WS_MEMBER_NON_CHANNEL)))
                .andExpect(status().isOk());

        // 2 人目（mika: ws-1 メンバーだが secret-pj 非メンバー）受諾 → 410
        mockMvc.perform(post("/api/channel-invites/" + rawToken + "/accept")
                        .header("Authorization", "Bearer " + login(WS_MEMBER_NON_CHANNEL_2)))
                .andExpect(status().isGone());
    }

    @Test
    void acceptWithoutTokenReturns401() throws Exception {
        mockMvc.perform(post("/api/channel-invites/whatever/accept"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- DELETE /api/channels/{id}/invites/{inviteId}（無効化） ----------

    @Test
    void revokeByChannelMemberReturns204AndBlocksAccept() throws Exception {
        String ownerToken = login(CHANNEL_MEMBER);
        String rawToken = issueInvite(ownerToken, PRIVATE_CHANNEL_ID, "{}");
        long inviteId = inviteIdOf(rawToken);

        mockMvc.perform(delete("/api/channels/" + PRIVATE_CHANNEL_ID + "/invites/" + inviteId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/channel-invites/" + rawToken + "/accept")
                        .header("Authorization", "Bearer " + login(WS_MEMBER_NON_CHANNEL)))
                .andExpect(status().isGone());
    }

    @Test
    void revokeByNonChannelMemberReturns403() throws Exception {
        String rawToken = issueInvite(login(CHANNEL_MEMBER), PRIVATE_CHANNEL_ID, "{}");
        long inviteId = inviteIdOf(rawToken);

        // haruka は secret-pj の非メンバー
        mockMvc.perform(delete("/api/channels/" + PRIVATE_CHANNEL_ID + "/invites/" + inviteId)
                        .header("Authorization", "Bearer " + login(WS_MEMBER_NON_CHANNEL)))
                .andExpect(status().isForbidden());
    }

    @Test
    void revokeInviteOfAnotherChannelReturns404() throws Exception {
        String ownerToken = login(CHANNEL_MEMBER);
        String rawToken = issueInvite(ownerToken, PRIVATE_CHANNEL_ID, "{}");
        long inviteId = inviteIdOf(rawToken);

        // secret-pj の招待を general のパスで無効化しようとする → findByIdAndChannelId 不一致で 404
        // （keisuke は general のメンバーでもあるため requireChannelMember は通過する）
        mockMvc.perform(delete("/api/channels/" + GENERAL_CHANNEL_ID + "/invites/" + inviteId)
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

    // ワークスペースを新規作成して ID を返す。
    private long createWorkspace(String ownerToken, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/workspaces")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"%s\"}".formatted(name)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    // チャンネルを新規作成して ID を返す。
    private long createChannel(String ownerToken, long wsId, String name, String type) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/workspaces/" + wsId + "/channels")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"%s\",\"type\":\"%s\"}".formatted(name, type)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    // 招待を発行して平文トークンを返す。
    private String issueInvite(String token, long channelId, String requestBody) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/channels/" + channelId + "/invites")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    // 平文トークンに対応する招待 ID を token_hash 経由で引く（テスト専用の簡便策）。
    private long inviteIdOf(String rawToken) {
        return channelInviteRepository.findByTokenHash(new InviteTokenService().hash(rawToken))
                .orElseThrow().getId();
    }
}
