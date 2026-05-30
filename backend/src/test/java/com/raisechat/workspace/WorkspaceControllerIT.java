package com.raisechat.workspace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raisechat.channel.ChannelMemberRepository;
import org.junit.jupiter.api.Assertions;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class WorkspaceControllerIT {

    // seed:
    //   users:   keisuke=1, haruka=2, ryo=3, mika=4, kenta=5
    //   ws-1 (RaiseTech AI, id=1): keisuke=OWNER, haruka/ryo/mika/kenta=MEMBER
    //   ws-2 (Side Project, id=2): keisuke のみ OWNER
    //   channels(ws-1): general=1, random=2, dev-backend=3, secret-pj=4, design=5
    //     haruka 参加チャンネル: general(1), random(2), dev-backend(3)
    private static final long WS_1 = 1L;
    private static final long WS_2 = 2L;
    private static final long HARUKA_ID = 2L;
    private static final long KEISUKE_ID = 1L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ChannelMemberRepository channelMemberRepository;

    @Autowired
    private WorkspaceMemberRepository workspaceMemberRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final String PASSWORD = "password";

    @BeforeEach
    void clearUnreadCache() {
        Set<String> keys = redisTemplate.keys("unread:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
        Set<String> mentionKeys = redisTemplate.keys("mention:*");
        if (mentionKeys != null && !mentionKeys.isEmpty()) {
            redisTemplate.delete(mentionKeys);
        }
    }

    @Test
    void createWorkspaceReturns201() throws Exception {
        String token = login("keisuke");

        mockMvc.perform(post("/api/workspaces")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"New WS\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("New WS"));
    }

    @Test
    void createWorkspaceWithoutTokenReturns401() throws Exception {
        mockMvc.perform(post("/api/workspaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"New WS\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getWorkspaceDetailReturnsMembers() throws Exception {
        String token = login("keisuke");

        // ws-1 (RaiseTech AI) の detail を取得。seed で id=1
        mockMvc.perform(get("/api/workspaces/1")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.members").isArray());
    }

    @Test
    void getWorkspaceDetailForNonMemberReturns403() throws Exception {
        // kenta は ws-2 (Side Project) のメンバーではない。seed で id=2
        String token = login("kenta");

        mockMvc.perform(get("/api/workspaces/2")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    // ---------- DELETE /api/workspaces/{wsId}/members/{userId}（F-16 キック） ----------

    @Test
    void kickMemberByOwnerReturns204AndMarksLeftAndClearsUnread() throws Exception {
        // 事前条件: haruka は ws-1 と そのチャンネル general(1) のアクティブメンバー。
        Assertions.assertTrue(
                channelMemberRepository.findByChannelIdAndUserIdAndLeftAtIsNull(1L, HARUKA_ID).isPresent());

        // haruka の未読/メンションバッジを Redis に積んでおく（キックで消えることを確認するため）。
        redisTemplate.opsForHash().put("unread:" + HARUKA_ID, "channel:1", "3");
        redisTemplate.opsForHash().put("mention:" + HARUKA_ID, "channel:1", "1");

        String ownerToken = login("keisuke");
        mockMvc.perform(delete("/api/workspaces/" + WS_1 + "/members/" + HARUKA_ID)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());

        // ワークスペースメンバーシップが論理退出している（アクティブ行が無い）
        Assertions.assertTrue(
                workspaceMemberRepository.findByWorkspaceIdAndUserIdAndLeftAtIsNull(WS_1, HARUKA_ID).isEmpty(),
                "workspace_members.left_at がセットされていること");

        // 参加していた全チャンネル（general/random/dev-backend）が論理退出している
        Assertions.assertTrue(channelMemberRepository.findByChannelIdAndUserIdAndLeftAtIsNull(1L, HARUKA_ID).isEmpty());
        Assertions.assertTrue(channelMemberRepository.findByChannelIdAndUserIdAndLeftAtIsNull(2L, HARUKA_ID).isEmpty());
        Assertions.assertTrue(channelMemberRepository.findByChannelIdAndUserIdAndLeftAtIsNull(3L, HARUKA_ID).isEmpty());

        // Redis の未読/メンションバッジが消えている
        Assertions.assertFalse(redisTemplate.opsForHash().hasKey("unread:" + HARUKA_ID, "channel:1"));
        Assertions.assertFalse(redisTemplate.opsForHash().hasKey("mention:" + HARUKA_ID, "channel:1"));
    }

    @Test
    void kickByNonOwnerReturns403() throws Exception {
        // haruka は ws-1 の MEMBER（OWNER ではない）→ ryo をキックできない
        String memberToken = login("haruka");

        mockMvc.perform(delete("/api/workspaces/" + WS_1 + "/members/3")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Forbidden"));
    }

    @Test
    void kickSelfReturns409() throws Exception {
        // OWNER が自分自身をキックしようとする → 409
        String ownerToken = login("keisuke");

        mockMvc.perform(delete("/api/workspaces/" + WS_1 + "/members/" + KEISUKE_ID)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Conflict"));
    }

    @Test
    void kickNonMemberReturns404() throws Exception {
        // ws-2 (Side Project) のメンバーは keisuke のみ。haruka は非メンバー → 404
        String ownerToken = login("keisuke");

        mockMvc.perform(delete("/api/workspaces/" + WS_2 + "/members/" + HARUKA_ID)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void kickWithoutTokenReturns401() throws Exception {
        mockMvc.perform(delete("/api/workspaces/" + WS_1 + "/members/" + HARUKA_ID))
                .andExpect(status().isUnauthorized());
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
}
