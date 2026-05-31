package com.raisechat.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raisechat.storage.ObjectStorage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class AvatarUploadControllerIT {

    private static final String SEED_USER = "keisuke";
    private static final String SEED_PASSWORD = "password";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // 実 S3 / LocalStack に接続させないため、ストレージはモックに差し替える。
    @MockitoBean
    private ObjectStorage objectStorage;

    // ---------- happy path ----------

    @Test
    void uploadPngReturnsUpdatedUserWithAvatarUrl() throws Exception {
        String token = loginAndGetAccess();
        String uploadedUrl = "http://localhost:4566/raisechat-avatars/avatars/1/abc.png";
        when(objectStorage.upload(anyString(), any(), eq("image/png"))).thenReturn(uploadedUrl);

        MockMultipartFile file = new MockMultipartFile(
                "file", "avatar.png", "image/png", new byte[]{1, 2, 3, 4});

        mockMvc.perform(multipart("/api/users/me/avatar")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(SEED_USER))
                .andExpect(jsonPath("$.avatarUrl").value(uploadedUrl));
    }

    @Test
    void uploadIsPersistedAndVisibleFromAuthMe() throws Exception {
        String token = loginAndGetAccess();
        String uploadedUrl = "http://localhost:4566/raisechat-avatars/avatars/1/persisted.jpg";
        when(objectStorage.upload(anyString(), any(), eq("image/jpeg"))).thenReturn(uploadedUrl);

        MockMultipartFile file = new MockMultipartFile(
                "file", "avatar.jpg", "image/jpeg", new byte[]{10, 20, 30});

        mockMvc.perform(multipart("/api/users/me/avatar")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarUrl").value(uploadedUrl));
    }

    // ---------- 415 Unsupported Media Type ----------

    @Test
    void unsupportedContentTypeReturns415() throws Exception {
        String token = loginAndGetAccess();

        MockMultipartFile file = new MockMultipartFile(
                "file", "note.txt", "text/plain", "hello".getBytes());

        mockMvc.perform(multipart("/api/users/me/avatar")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415))
                .andExpect(jsonPath("$.title").value("Unsupported Media Type"));

        verify(objectStorage, never()).upload(anyString(), any(), anyString());
    }

    // ---------- 413 Payload Too Large ----------

    @Test
    void oversizedImageReturns413() throws Exception {
        String token = loginAndGetAccess();

        // 2MB + 1 バイト（multipart 上限 10MB 未満なのでサービス層の判定に到達する）。
        byte[] big = new byte[2 * 1024 * 1024 + 1];
        MockMultipartFile file = new MockMultipartFile(
                "file", "big.png", "image/png", big);

        mockMvc.perform(multipart("/api/users/me/avatar")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.status").value(413))
                .andExpect(jsonPath("$.title").value("Payload Too Large"));

        verify(objectStorage, never()).upload(anyString(), any(), anyString());
    }

    // ---------- 422 empty file ----------

    @Test
    void emptyFileReturns422() throws Exception {
        String token = loginAndGetAccess();

        MockMultipartFile file = new MockMultipartFile(
                "file", "empty.png", "image/png", new byte[0]);

        mockMvc.perform(multipart("/api/users/me/avatar")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422));
    }

    // ---------- auth ----------

    @Test
    void withoutTokenReturns401() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "avatar.png", "image/png", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/users/me/avatar")
                        .file(file))
                .andExpect(status().isUnauthorized());
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
