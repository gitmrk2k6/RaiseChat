package com.raisechat.dm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

// seed の対応関係:
//   - workspace 1 (RaiseTech AI): keisuke / haruka / ryo / mika / kenta
//   - workspace 2 (Side Project): keisuke のみ（他は非メンバー）
//   - DM room 1 (ws=1): keisuke ⇔ haruka
//   - DM room 2 (ws=1): keisuke ⇔ ryo
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class DmControllerIT {

    private static final long WS_RAISETECH = 1L;
    private static final long WS_SIDE_PROJECT = 2L;

    private static final String OWNER = "keisuke";
    private static final String MEMBER_HARUKA = "haruka";
    private static final String MEMBER_RYO = "ryo";
    private static final String MEMBER_MIKA = "mika";
    private static final String PASSWORD = "password";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    // ============================================================
    // POST /api/workspaces/{wsId}/dm/rooms
    // ============================================================

    @Test
    void createReturnsExistingRoomWith200() throws Exception {
        // seed で room 1 (keisuke ⇔ haruka) が既に存在
        String token = login(OWNER);
        Long partnerId = userIdOf(MEMBER_HARUKA);

        mockMvc.perform(post("/api/workspaces/" + WS_RAISETECH + "/dm/rooms")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"partnerUserId\":" + partnerId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.workspaceId").value(WS_RAISETECH))
                .andExpect(jsonPath("$.members").isArray())
                .andExpect(jsonPath("$.members.length()").value(2));
    }

    @Test
    void createNewRoomReturns201() throws Exception {
        // seed では keisuke ⇔ mika の DM はまだ無いので新規作成
        String token = login(OWNER);
        Long partnerId = userIdOf(MEMBER_MIKA);

        mockMvc.perform(post("/api/workspaces/" + WS_RAISETECH + "/dm/rooms")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"partnerUserId\":" + partnerId + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.workspaceId").value(WS_RAISETECH))
                .andExpect(jsonPath("$.members.length()").value(2));
    }

    @Test
    void createWithSelfReturns422() throws Exception {
        String token = login(OWNER);
        Long selfId = userIdOf(OWNER);

        mockMvc.perform(post("/api/workspaces/" + WS_RAISETECH + "/dm/rooms")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"partnerUserId\":" + selfId + "}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422));
    }

    @Test
    void createWithNonExistentPartnerReturns404() throws Exception {
        String token = login(OWNER);

        mockMvc.perform(post("/api/workspaces/" + WS_RAISETECH + "/dm/rooms")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"partnerUserId\":99999999}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void createWithPartnerOutsideWorkspaceReturns403() throws Exception {
        // workspace 2 (Side Project) は keisuke のみ。haruka を相手指定 → 403
        String token = login(OWNER);
        Long partnerId = userIdOf(MEMBER_HARUKA);

        mockMvc.perform(post("/api/workspaces/" + WS_SIDE_PROJECT + "/dm/rooms")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"partnerUserId\":" + partnerId + "}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void createByNonWorkspaceMemberReturns403() throws Exception {
        // haruka は Side Project の非メンバー → そこで作成依頼 → 403
        String token = login(MEMBER_HARUKA);
        Long partnerId = userIdOf(OWNER);

        mockMvc.perform(post("/api/workspaces/" + WS_SIDE_PROJECT + "/dm/rooms")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"partnerUserId\":" + partnerId + "}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void createWithoutTokenReturns401() throws Exception {
        mockMvc.perform(post("/api/workspaces/" + WS_RAISETECH + "/dm/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"partnerUserId\":2}"))
                .andExpect(status().isUnauthorized());
    }

    // ============================================================
    // GET /api/workspaces/{wsId}/dm/rooms
    // ============================================================

    @Test
    void listMyRoomsAsOwner() throws Exception {
        // keisuke は seed で 2 部屋に参加
        String token = login(OWNER);

        mockMvc.perform(get("/api/workspaces/" + WS_RAISETECH + "/dm/rooms")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(2));
    }

    @Test
    void listMyRoomsAsMember() throws Exception {
        // haruka は room 1 のみ参加
        String token = login(MEMBER_HARUKA);

        mockMvc.perform(get("/api/workspaces/" + WS_RAISETECH + "/dm/rooms")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void listByNonWorkspaceMemberReturns403() throws Exception {
        // haruka は Side Project 非メンバー
        String token = login(MEMBER_HARUKA);

        mockMvc.perform(get("/api/workspaces/" + WS_SIDE_PROJECT + "/dm/rooms")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void listWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/workspaces/" + WS_RAISETECH + "/dm/rooms"))
                .andExpect(status().isUnauthorized());
    }

    // ============================================================
    // GET /api/dm/rooms/{id}/messages
    // ============================================================

    @Test
    void listDmMessagesHappyPath() throws Exception {
        // room 1 (keisuke ⇔ haruka) を keisuke 側から取得
        String token = login(OWNER);
        Long roomId = dmRoomIdOf(OWNER, MEMBER_HARUKA, WS_RAISETECH);

        mockMvc.perform(get("/api/dm/rooms/" + roomId + "/messages")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items[0].dmRoomId").value(roomId))
                .andExpect(jsonPath("$.items[0].channelId").doesNotExist())
                .andExpect(jsonPath("$.items[0].body").isString());
    }

    @Test
    void listDmMessagesByPartner() throws Exception {
        // haruka 側からも見える
        String token = login(MEMBER_HARUKA);
        Long roomId = dmRoomIdOf(OWNER, MEMBER_HARUKA, WS_RAISETECH);

        mockMvc.perform(get("/api/dm/rooms/" + roomId + "/messages")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(3));
    }

    @Test
    void listDmMessagesByNonMemberReturns403() throws Exception {
        // ryo は room 1 のメンバーではない
        String token = login(MEMBER_RYO);
        Long roomId = dmRoomIdOf(OWNER, MEMBER_HARUKA, WS_RAISETECH);

        mockMvc.perform(get("/api/dm/rooms/" + roomId + "/messages")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void listDmMessagesForNonExistentRoomReturns404() throws Exception {
        String token = login(OWNER);

        mockMvc.perform(get("/api/dm/rooms/99999999/messages")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void listDmMessagesWithoutTokenReturns401() throws Exception {
        Long roomId = dmRoomIdOf(OWNER, MEMBER_HARUKA, WS_RAISETECH);

        mockMvc.perform(get("/api/dm/rooms/" + roomId + "/messages"))
                .andExpect(status().isUnauthorized());
    }

    // ============================================================
    // helpers
    // ============================================================

    private Long userIdOf(String userId) {
        return userRepository.findByUserId(userId)
                .map(User::getId)
                .orElseThrow();
    }

    private Long dmRoomIdOf(String userA, String userB, Long workspaceId) throws Exception {
        // seed の room を partner 指定の POST で取り出す（既存があれば 200 で返す動作）
        String token = login(userA);
        Long partnerId = userIdOf(userB);
        MvcResult result = mockMvc.perform(post("/api/workspaces/" + workspaceId + "/dm/rooms")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"partnerUserId\":" + partnerId + "}"))
                .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("id").asLong();
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
