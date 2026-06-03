package com.raisechat.message;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raisechat.storage.ObjectStorage;
import com.raisechat.user.UserRepository;
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
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// F-10 添付込みメッセージ作成 API（multipart）の結合テスト。
//   POST /api/channels/{id}/messages
//   POST /api/dm/rooms/{id}/messages
// seed: general(id=1) は全員メンバー / secret-pj(id=4, PRIVATE) は keisuke のみ、mika は非メンバー。
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class MessageWithAttachmentsControllerIT {

    private static final long CH_GENERAL = 1L;
    private static final long CH_SECRET_PJ = 4L;
    private static final long WS_RAISETECH = 1L;

    private static final String OWNER = "keisuke";
    private static final String NON_MEMBER = "mika";   // secret-pj には未参加
    private static final String DM_PARTNER = "haruka";
    private static final String PASSWORD = "password";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    // 実 S3 / LocalStack に接続させないため、ストレージはモックに差し替える。
    @MockitoBean
    private ObjectStorage objectStorage;

    // ---------- channel happy path ----------

    @Test
    void createChannelMessageWithAttachmentReturns201() throws Exception {
        String token = login(OWNER);
        String url = "http://localhost:4566/raisechat-uploads/attachments/x/diagram.png";
        when(objectStorage.upload(anyString(), any(), eq("image/png"))).thenReturn(url);

        MockMultipartFile file = new MockMultipartFile(
                "files", "diagram.png", "image/png", new byte[]{1, 2, 3, 4});

        mockMvc.perform(multipart("/api/channels/" + CH_GENERAL + "/messages")
                        .file(file)
                        .param("body", "図を共有します")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.channelId").value(CH_GENERAL))
                .andExpect(jsonPath("$.body").value("図を共有します"))
                .andExpect(jsonPath("$.attachments.length()").value(1))
                .andExpect(jsonPath("$.attachments[0].url").value(url))
                .andExpect(jsonPath("$.attachments[0].originalFilename").value("diagram.png"));
    }

    // ---------- 422 本文が空 ----------

    @Test
    void createWithBlankBodyReturns422() throws Exception {
        String token = login(OWNER);
        MockMultipartFile file = new MockMultipartFile(
                "files", "a.png", "image/png", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/channels/" + CH_GENERAL + "/messages")
                        .file(file)
                        .param("body", "   ")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422));
    }

    // ---------- 422 ファイル数超過（最大 10） ----------

    @Test
    void createWithTooManyFilesReturns422() throws Exception {
        String token = login(OWNER);

        MockMultipartHttpServletRequestBuilder req = multipart("/api/channels/" + CH_GENERAL + "/messages");
        for (int i = 0; i < 11; i++) {
            req.file(new MockMultipartFile("files", "f" + i + ".png", "image/png", new byte[]{1}));
        }

        mockMvc.perform(req
                        .param("body", "たくさん添付")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422));
    }

    // ---------- 403 非メンバーは投稿不可 ----------

    @Test
    void createByNonChannelMemberReturns403() throws Exception {
        String token = login(NON_MEMBER);
        MockMultipartFile file = new MockMultipartFile(
                "files", "a.png", "image/png", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/channels/" + CH_SECRET_PJ + "/messages")
                        .file(file)
                        .param("body", "入れないはず")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    // ---------- DM happy path ----------

    @Test
    void createDmMessageWithAttachmentReturns201() throws Exception {
        String token = login(OWNER);
        long roomId = dmRoomIdWith(DM_PARTNER);
        String url = "http://localhost:4566/raisechat-uploads/attachments/dm/clip.png";
        when(objectStorage.upload(anyString(), any(), eq("image/png"))).thenReturn(url);

        MockMultipartFile file = new MockMultipartFile(
                "files", "clip.png", "image/png", new byte[]{5, 6, 7});

        mockMvc.perform(multipart("/api/dm/rooms/" + roomId + "/messages")
                        .file(file)
                        .param("body", "DM に添付")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.dmRoomId").value(roomId))
                .andExpect(jsonPath("$.body").value("DM に添付"))
                .andExpect(jsonPath("$.attachments.length()").value(1))
                .andExpect(jsonPath("$.attachments[0].url").value(url));
    }

    // ---------- 401 ----------

    @Test
    void createWithoutTokenReturns401() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "files", "a.png", "image/png", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/channels/" + CH_GENERAL + "/messages")
                        .file(file)
                        .param("body", "no token"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- helpers ----------

    // 相手指定で DM ルームを作成/取得し、その id を返す（既存があれば 200 で返る）。
    private long dmRoomIdWith(String partnerUserId) throws Exception {
        String token = login(OWNER);
        long partnerId = userRepository.findByUserId(partnerUserId).orElseThrow().getId();
        MvcResult result = mockMvc.perform(post("/api/workspaces/" + WS_RAISETECH + "/dm/rooms")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"partnerUserId\":" + partnerId + "}"))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
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
