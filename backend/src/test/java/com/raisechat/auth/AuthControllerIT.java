package com.raisechat.auth;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class AuthControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // ---------- signup ----------

    @Test
    void signupHappyPath() throws Exception {
        String body = """
                {"userId":"newuser_happy","displayName":"New User","password":"password123"}
                """;
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.expiresIn").value(900));
    }

    @Test
    void signupDuplicateUserIdReturns409() throws Exception {
        // seed の keisuke と重複
        String body = """
                {"userId":"keisuke","displayName":"Dup","password":"password123"}
                """;
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void signupInvalidUserIdReturns422() throws Exception {
        // userId に記号を含む（バリデーション違反）→ 他 API と揃えて 422
        String body = """
                {"userId":"bad!user","displayName":"X","password":"password123"}
                """;
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity());
    }

    // ---------- login ----------

    @Test
    void loginWithSeedUserSucceeds() throws Exception {
        String body = """
                {"userId":"keisuke","password":"password"}
                """;
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());
    }

    @Test
    void loginWithWrongPasswordReturns401() throws Exception {
        String body = """
                {"userId":"keisuke","password":"wrong-password"}
                """;
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginWithUnknownUserReturns401() throws Exception {
        String body = """
                {"userId":"nobody","password":"password"}
                """;
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    // ---------- refresh ----------

    @Test
    void refreshRotatesTokenAndOldOneBecomesInvalid() throws Exception {
        String refreshToken1 = loginAndGetRefresh("keisuke", "password");

        // 1 回目の refresh は成功し、新しい token が返る
        String refreshToken2 = refreshAndGetRefresh(refreshToken1);

        // 旧 refreshToken1 は revoke されているので 401
        String body = """
                {"refreshToken":"%s"}
                """.formatted(refreshToken1);
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());

        // 新 refreshToken2 は引き続き有効
        String body2 = """
                {"refreshToken":"%s"}
                """.formatted(refreshToken2);
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body2))
                .andExpect(status().isOk());
    }

    @Test
    void refreshWithUnknownTokenReturns401() throws Exception {
        String body = """
                {"refreshToken":"this-token-does-not-exist"}
                """;
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    // ---------- me ----------

    @Test
    void meWithValidTokenReturnsUserInfo() throws Exception {
        String accessToken = loginAndGetAccess("keisuke", "password");

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("keisuke"))
                .andExpect(jsonPath("$.displayName").value("Keisuke Konishi"));
    }

    @Test
    void meWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meWithInvalidTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer not-a-valid-jwt"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- helpers ----------

    private String loginAndGetAccess(String userId, String password) throws Exception {
        return loginJson(userId, password).get("accessToken").asText();
    }

    private String loginAndGetRefresh(String userId, String password) throws Exception {
        return loginJson(userId, password).get("refreshToken").asText();
    }

    private JsonNode loginJson(String userId, String password) throws Exception {
        String body = """
                {"userId":"%s","password":"%s"}
                """.formatted(userId, password);
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String refreshAndGetRefresh(String refreshToken) throws Exception {
        String body = """
                {"refreshToken":"%s"}
                """.formatted(refreshToken);
        MvcResult result = mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("refreshToken").asText();
    }
}
