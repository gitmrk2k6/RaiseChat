package com.raisechat.workspace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hamcrest.Matchers;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class WorkspaceControllerIT {

    private static final String OWNER_USER = "keisuke";
    private static final String OWNER_PASSWORD = "password";
    private static final String NON_OWNER_USER = "haruka";
    private static final String NON_OWNER_PASSWORD = "password";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // ---------- POST /api/workspaces ----------

    @Test
    void createHappyPath() throws Exception {
        String token = loginAndGetAccess(OWNER_USER, OWNER_PASSWORD);

        String body = """
                {"name":"New WS","description":"hello"}
                """;
        mockMvc.perform(post("/api/workspaces")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("New WS"))
                .andExpect(jsonPath("$.description").value("hello"))
                .andExpect(jsonPath("$.ownerUserId").isNumber())
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    @Test
    void createWithoutTokenReturns401() throws Exception {
        mockMvc.perform(post("/api/workspaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createBlankNameReturns422() throws Exception {
        String token = loginAndGetAccess(OWNER_USER, OWNER_PASSWORD);

        mockMvc.perform(post("/api/workspaces")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.title").value("Validation Failed"))
                .andExpect(jsonPath("$.errors[0].field").value("name"));
    }

    @Test
    void createTooLongNameReturns422() throws Exception {
        String token = loginAndGetAccess(OWNER_USER, OWNER_PASSWORD);

        String longName = "a".repeat(65);
        String body = """
                {"name":"%s"}
                """.formatted(longName);
        mockMvc.perform(post("/api/workspaces")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("name"));
    }

    @Test
    void createPersistsAndCreatorBecomesOwnerMember() throws Exception {
        String token = loginAndGetAccess(OWNER_USER, OWNER_PASSWORD);

        String body = """
                {"name":"Solo WS","description":""}
                """;
        MvcResult result = mockMvc.perform(post("/api/workspaces")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode created = objectMapper.readTree(result.getResponse().getContentAsString());
        long newId = created.get("id").asLong();

        mockMvc.perform(get("/api/workspaces/" + newId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(newId))
                .andExpect(jsonPath("$.name").value("Solo WS"))
                .andExpect(jsonPath("$.members.length()").value(1))
                .andExpect(jsonPath("$.members[0].userId").value(OWNER_USER))
                .andExpect(jsonPath("$.members[0].role").value("OWNER"));
    }

    // ---------- GET /api/workspaces ----------

    @Test
    void listIncludesSeedWorkspacesForOwner() throws Exception {
        String token = loginAndGetAccess(OWNER_USER, OWNER_PASSWORD);

        mockMvc.perform(get("/api/workspaces")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].name").value("RaiseTech AI"))
                .andExpect(jsonPath("$.items[1].name").value("Side Project"))
                .andExpect(jsonPath("$.hasMore").value(false))
                .andExpect(jsonPath("$.nextCursor").value(Matchers.nullValue()));
    }

    @Test
    void listForNonOwnerSeesOnlyMemberWorkspaces() throws Exception {
        // haruka は seed で ws-1 だけのメンバー（ws-2 には未参加）
        String token = loginAndGetAccess(NON_OWNER_USER, NON_OWNER_PASSWORD);

        mockMvc.perform(get("/api/workspaces")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].name").value("RaiseTech AI"))
                .andExpect(jsonPath("$.hasMore").value(false));
    }

    @Test
    void listWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/workspaces"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listPagesWithCursor() throws Exception {
        String token = loginAndGetAccess(OWNER_USER, OWNER_PASSWORD);

        // limit=1 で 1 ページ目: ws-1 のみ、hasMore=true, nextCursor="1"
        mockMvc.perform(get("/api/workspaces?limit=1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(1))
                .andExpect(jsonPath("$.hasMore").value(true))
                .andExpect(jsonPath("$.nextCursor").value("1"));

        // 2 ページ目: cursor=1 → ws-2 のみ、hasMore=false
        mockMvc.perform(get("/api/workspaces?limit=1&cursor=1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(2))
                .andExpect(jsonPath("$.hasMore").value(false))
                .andExpect(jsonPath("$.nextCursor").value(Matchers.nullValue()));
    }

    // ---------- GET /api/workspaces/{wsId} ----------

    @Test
    void getDetailForMemberReturnsMembersList() throws Exception {
        String token = loginAndGetAccess(OWNER_USER, OWNER_PASSWORD);

        mockMvc.perform(get("/api/workspaces/1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("RaiseTech AI"))
                .andExpect(jsonPath("$.members.length()").value(5))
                .andExpect(jsonPath("$.members[0].userId").value(OWNER_USER))
                .andExpect(jsonPath("$.members[0].role").value("OWNER"));
    }

    @Test
    void getDetailForNonMemberReturns403() throws Exception {
        // haruka は ws-2 (Side Project) には未参加
        String token = loginAndGetAccess(NON_OWNER_USER, NON_OWNER_PASSWORD);

        mockMvc.perform(get("/api/workspaces/2")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.title").value("Forbidden"));
    }

    @Test
    void getDetailForUnknownIdReturns404() throws Exception {
        String token = loginAndGetAccess(OWNER_USER, OWNER_PASSWORD);

        mockMvc.perform(get("/api/workspaces/999999")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Resource Not Found"));
    }

    @Test
    void getDetailWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/workspaces/1"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- helpers ----------

    private String loginAndGetAccess(String userId, String password) throws Exception {
        String body = """
                {"userId":"%s","password":"%s"}
                """.formatted(userId, password);
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("accessToken").asText();
    }
}
