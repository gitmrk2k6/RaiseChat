package com.raisechat.channel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// チャンネルメンバー管理 API の結合テスト。
//   POST   /api/channels/{id}/members            （直接追加 = 招待, #168/#169）
//   GET    /api/channels/{id}/members            （一覧, #170）
//   DELETE /api/channels/{id}/members/{userId}   （キック, #170）
//
// seed.sql の対応関係（ChannelControllerIT と同じ）:
//   - keisuke: ws-1 OWNER。general/random/dev-backend/secret-pj/design 全チャンネルのメンバー
//   - haruka:  ws-1 MEMBER。general/random/dev-backend のメンバー（secret-pj には未参加）
//   - mika:    ws-1 MEMBER。general/random/design のメンバー（dev-backend/secret-pj には未参加）
//   channel id: 1 general, 3 dev-backend, 4 secret-pj(PRIVATE)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class ChannelMemberControllerIT {

    private static final long CH_GENERAL = 1L;
    private static final long CH_DEV_BACKEND = 3L;
    private static final long CH_SECRET_PJ = 4L;

    private static final String OWNER = "keisuke";
    private static final String MEMBER = "haruka";
    private static final String NON_DEV_MEMBER = "mika";
    private static final String PASSWORD = "password";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    // ============================================================
    // POST /api/channels/{id}/members （直接追加）
    // ============================================================

    @Test
    void addMembersHappyPath() throws Exception {
        String token = login(OWNER);
        long mikaId = userIdOf(NON_DEV_MEMBER);

        // keisuke は secret-pj のメンバーなので mika を追加できる。
        mockMvc.perform(post("/api/channels/" + CH_SECRET_PJ + "/members")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userIds\":[" + mikaId + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(CH_SECRET_PJ));

        assertTrue(memberUserIds(CH_SECRET_PJ, token).contains(mikaId),
                "追加後の一覧に mika が含まれる");
    }

    @Test
    void addMembersIsIdempotentForExistingMember() throws Exception {
        String token = login(OWNER);
        long harukaId = userIdOf(MEMBER);

        // haruka は既に dev-backend のメンバー。再追加しても 200 で重複しない。
        mockMvc.perform(post("/api/channels/" + CH_DEV_BACKEND + "/members")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userIds\":[" + harukaId + "]}"))
                .andExpect(status().isOk());

        long count = memberUserIds(CH_DEV_BACKEND, token).stream().filter(id -> id == harukaId).count();
        assertEquals(1, count, "haruka は重複せず 1 件");
    }

    @Test
    void addMembersByNonChannelMemberReturns403() throws Exception {
        // mika は secret-pj のメンバーではないので追加操作はできない。
        String token = login(NON_DEV_MEMBER);
        long kentaId = userIdOf("kenta");

        mockMvc.perform(post("/api/channels/" + CH_SECRET_PJ + "/members")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userIds\":[" + kentaId + "]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void addMembersEmptyListReturns422() throws Exception {
        String token = login(OWNER);

        mockMvc.perform(post("/api/channels/" + CH_DEV_BACKEND + "/members")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userIds\":[]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422));
    }

    @Test
    void addMembersWithoutTokenReturns401() throws Exception {
        mockMvc.perform(post("/api/channels/" + CH_DEV_BACKEND + "/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userIds\":[1]}"))
                .andExpect(status().isUnauthorized());
    }

    // ============================================================
    // GET /api/channels/{id}/members （一覧）
    // ============================================================

    @Test
    void listMembersHappyPath() throws Exception {
        String token = login(OWNER);

        MvcResult result = mockMvc.perform(get("/api/channels/" + CH_DEV_BACKEND + "/members")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode arr = objectMapper.readTree(result.getResponse().getContentAsString());
        assertTrue(arr.isArray());
        assertTrue(userIdsOf(arr).contains(userIdOf(OWNER)), "keisuke が含まれる");
        assertTrue(userIdsOf(arr).contains(userIdOf(MEMBER)), "haruka が含まれる");
    }

    @Test
    void listMembersOfPrivateChannelByNonMemberReturns403() throws Exception {
        // mika は secret-pj(PRIVATE) のメンバーではないため一覧も見られない。
        String token = login(NON_DEV_MEMBER);

        mockMvc.perform(get("/api/channels/" + CH_SECRET_PJ + "/members")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    // ============================================================
    // DELETE /api/channels/{id}/members/{userId} （キック）
    // ============================================================

    @Test
    void kickMemberHappyPath() throws Exception {
        String token = login(OWNER);
        long harukaId = userIdOf(MEMBER);

        // keisuke(OWNER) が dev-backend から haruka を除外。
        mockMvc.perform(delete("/api/channels/" + CH_DEV_BACKEND + "/members/" + harukaId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        assertFalse(memberUserIds(CH_DEV_BACKEND, token).contains(harukaId),
                "除外後の一覧に haruka は含まれない");
    }

    @Test
    void kickByNonOwnerNonCreatorReturns403() throws Exception {
        // haruka は MEMBER かつ作成者でもないので、誰も除外できない。
        String token = login(MEMBER);
        long ownerId = userIdOf(OWNER);

        mockMvc.perform(delete("/api/channels/" + CH_DEV_BACKEND + "/members/" + ownerId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void kickFromGeneralReturns409() throws Exception {
        String token = login(OWNER);
        long harukaId = userIdOf(MEMBER);

        mockMvc.perform(delete("/api/channels/" + CH_GENERAL + "/members/" + harukaId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void kickSelfReturns409() throws Exception {
        String token = login(OWNER);
        long ownerId = userIdOf(OWNER);

        mockMvc.perform(delete("/api/channels/" + CH_DEV_BACKEND + "/members/" + ownerId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void kickWithoutTokenReturns401() throws Exception {
        mockMvc.perform(delete("/api/channels/" + CH_DEV_BACKEND + "/members/2"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- helpers ----------

    private java.util.List<Long> memberUserIds(long channelId, String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/channels/" + channelId + "/members")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return userIdsOf(objectMapper.readTree(result.getResponse().getContentAsString()));
    }

    private java.util.List<Long> userIdsOf(JsonNode arr) {
        java.util.List<Long> ids = new java.util.ArrayList<>();
        for (JsonNode m : arr) {
            ids.add(m.get("id").asLong());
        }
        return ids;
    }

    private long userIdOf(String userId) {
        return userRepository.findByUserId(userId).orElseThrow().getId();
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
