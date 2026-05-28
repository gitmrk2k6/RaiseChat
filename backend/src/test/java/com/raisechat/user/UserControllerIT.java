package com.raisechat.user;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class UserControllerIT {

    private static final String SEED_USER = "keisuke";
    private static final String SEED_PASSWORD = "password";
    private static final String SEED_DISPLAY_NAME = "Keisuke Konishi";
    private static final String SEED_STATUS_MESSAGE = "🎧 集中モード";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // ---------- happy paths ----------

    @Test
    void updateDisplayNameOnly() throws Exception {
        String token = loginAndGetAccess();

        String body = """
                {"displayName":"新しい表示名"}
                """;
        mockMvc.perform(put("/api/users/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(SEED_USER))
                .andExpect(jsonPath("$.displayName").value("新しい表示名"))
                .andExpect(jsonPath("$.statusMessage").value(SEED_STATUS_MESSAGE));
    }

    @Test
    void updateStatusMessageOnly() throws Exception {
        String token = loginAndGetAccess();

        String body = """
                {"statusMessage":"実装中"}
                """;
        mockMvc.perform(put("/api/users/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value(SEED_DISPLAY_NAME))
                .andExpect(jsonPath("$.statusMessage").value("実装中"));
    }

    @Test
    void updateBoth() throws Exception {
        String token = loginAndGetAccess();

        String body = """
                {"displayName":"Mrk2k6","statusMessage":"実装中"}
                """;
        mockMvc.perform(put("/api/users/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Mrk2k6"))
                .andExpect(jsonPath("$.statusMessage").value("実装中"));
    }

    @Test
    void updateEmptyBodyKeepsValues() throws Exception {
        String token = loginAndGetAccess();

        mockMvc.perform(put("/api/users/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value(SEED_DISPLAY_NAME))
                .andExpect(jsonPath("$.statusMessage").value(SEED_STATUS_MESSAGE));
    }

    @Test
    void updateAllowsEmptyStatusMessage() throws Exception {
        String token = loginAndGetAccess();

        String body = """
                {"statusMessage":""}
                """;
        mockMvc.perform(put("/api/users/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusMessage").value(""));
    }

    // ---------- validation (422 + ProblemDetail) ----------

    @Test
    void displayNameTooLongReturns422() throws Exception {
        String token = loginAndGetAccess();

        String longName = "a".repeat(33);
        String body = """
                {"displayName":"%s"}
                """.formatted(longName);

        mockMvc.perform(put("/api/users/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.title").value("Validation Failed"))
                .andExpect(jsonPath("$.errors[0].field").value("displayName"));
    }

    @Test
    void displayNameEmptyReturns422() throws Exception {
        String token = loginAndGetAccess();

        String body = """
                {"displayName":""}
                """;
        mockMvc.perform(put("/api/users/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("displayName"));
    }

    @Test
    void statusMessageTooLongReturns422() throws Exception {
        String token = loginAndGetAccess();

        String longStatus = "x".repeat(101);
        String body = """
                {"statusMessage":"%s"}
                """.formatted(longStatus);

        mockMvc.perform(put("/api/users/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("statusMessage"));
    }

    // ---------- auth ----------

    @Test
    void withoutTokenReturns401() throws Exception {
        mockMvc.perform(put("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"x\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void withInvalidTokenReturns401() throws Exception {
        mockMvc.perform(put("/api/users/me")
                        .header("Authorization", "Bearer not-a-valid-jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"x\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- persistence (/me で反映確認) ----------

    @Test
    void updateIsPersistedAndVisibleFromAuthMe() throws Exception {
        String token = loginAndGetAccess();

        String body = """
                {"displayName":"反映確認","statusMessage":"persisted"}
                """;
        mockMvc.perform(put("/api/users/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("反映確認"))
                .andExpect(jsonPath("$.statusMessage").value("persisted"));
    }

    // ---------- helpers ----------

    private String loginAndGetAccess() throws Exception {
        String body = """
                {"userId":"%s","password":"%s"}
                """.formatted(SEED_USER, SEED_PASSWORD);
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("accessToken").asText();
    }
}
