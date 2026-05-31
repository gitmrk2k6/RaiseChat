package com.raisechat.message;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raisechat.channel.Channel;
import com.raisechat.channel.ChannelRepository;
import com.raisechat.storage.ObjectStorage;
import com.raisechat.user.User;
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

// F-10 ファイル添付 API（POST /api/messages/{id}/attachments）の結合テスト。
// seed の general (id=1) は keisuke / haruka など全員メンバー。
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class AttachmentControllerIT {

    private static final long CH_GENERAL = 1L;
    private static final String OWNER = "keisuke";   // メッセージ投稿者
    private static final String OTHER = "haruka";    // 別ユーザー（添付不可）
    private static final String PASSWORD = "password";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ChannelRepository channelRepository;

    // 実 S3 / LocalStack に接続させないため、ストレージはモックに差し替える。
    @MockitoBean
    private ObjectStorage objectStorage;

    // ---------- happy path ----------

    @Test
    void uploadReturnsAttachmentForOwnMessage() throws Exception {
        String token = login(OWNER);
        Long messageId = createChannelMessage(OWNER, CH_GENERAL, "添付するメッセージ");
        String url = "http://localhost:4566/raisechat-uploads/attachments/" + messageId + "/x.png";
        when(objectStorage.upload(anyString(), any(), eq("image/png"))).thenReturn(url);

        MockMultipartFile file = new MockMultipartFile(
                "file", "diagram.png", "image/png", new byte[]{1, 2, 3, 4});

        mockMvc.perform(multipart("/api/messages/" + messageId + "/attachments")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.messageId").value(messageId))
                .andExpect(jsonPath("$.url").value(url))
                .andExpect(jsonPath("$.mimeType").value("image/png"))
                .andExpect(jsonPath("$.sizeBytes").value(4))
                .andExpect(jsonPath("$.originalFilename").value("diagram.png"));
    }

    @Test
    void uploadedAttachmentIsVisibleInMessageList() throws Exception {
        String token = login(OWNER);
        Long messageId = createChannelMessage(OWNER, CH_GENERAL, "一覧に添付が出るか");
        String uploadUrl = "http://localhost:4566/raisechat-uploads/attachments/" + messageId + "/y.mp4";
        when(objectStorage.upload(anyString(), any(), eq("video/mp4"))).thenReturn(uploadUrl);
        // 一覧取得時は s3Key からの URL 復元が使われる。
        when(objectStorage.resolveUrl(anyString())).thenReturn(uploadUrl);

        MockMultipartFile file = new MockMultipartFile(
                "file", "clip.mp4", "video/mp4", new byte[]{9, 8, 7});
        mockMvc.perform(multipart("/api/messages/" + messageId + "/attachments")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());

        MvcResult result = mockMvc.perform(get("/api/channels/" + CH_GENERAL + "/messages")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode items = objectMapper.readTree(result.getResponse().getContentAsString()).get("items");
        JsonNode target = null;
        for (JsonNode item : items) {
            if (item.get("id").asLong() == messageId) {
                target = item;
                break;
            }
        }
        org.junit.jupiter.api.Assertions.assertNotNull(target, "投稿したメッセージが一覧に存在する");
        JsonNode attachments = target.get("attachments");
        org.junit.jupiter.api.Assertions.assertEquals(1, attachments.size());
        org.junit.jupiter.api.Assertions.assertEquals("clip.mp4",
                attachments.get(0).get("originalFilename").asText());
        org.junit.jupiter.api.Assertions.assertEquals(uploadUrl,
                attachments.get(0).get("url").asText());
    }

    // ---------- 415 Unsupported Media Type ----------

    @Test
    void unsupportedContentTypeReturns415() throws Exception {
        String token = login(OWNER);
        Long messageId = createChannelMessage(OWNER, CH_GENERAL, "非対応形式");

        MockMultipartFile file = new MockMultipartFile(
                "file", "note.txt", "text/plain", "hello".getBytes());

        mockMvc.perform(multipart("/api/messages/" + messageId + "/attachments")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415))
                .andExpect(jsonPath("$.title").value("Unsupported Media Type"));

        verify(objectStorage, never()).upload(anyString(), any(), anyString());
    }

    // ---------- 413 Payload Too Large（サービス層 10MB 判定）----------

    @Test
    void oversizedFileReturns413() throws Exception {
        String token = login(OWNER);
        Long messageId = createChannelMessage(OWNER, CH_GENERAL, "サイズ超過");

        // 10MB + 1 バイト（servlet 上限 12MB 未満なのでサービス層の判定に到達する）。
        byte[] big = new byte[10 * 1024 * 1024 + 1];
        MockMultipartFile file = new MockMultipartFile(
                "file", "big.png", "image/png", big);

        mockMvc.perform(multipart("/api/messages/" + messageId + "/attachments")
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
        String token = login(OWNER);
        Long messageId = createChannelMessage(OWNER, CH_GENERAL, "空ファイル");

        MockMultipartFile file = new MockMultipartFile(
                "file", "empty.png", "image/png", new byte[0]);

        mockMvc.perform(multipart("/api/messages/" + messageId + "/attachments")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422));
    }

    // ---------- 403 他ユーザーのメッセージには添付不可 ----------

    @Test
    void attachingToOthersMessageReturns403() throws Exception {
        String otherToken = login(OTHER);
        Long messageId = createChannelMessage(OWNER, CH_GENERAL, "keisuke のメッセージ");

        MockMultipartFile file = new MockMultipartFile(
                "file", "x.png", "image/png", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/messages/" + messageId + "/attachments")
                        .file(file)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        verify(objectStorage, never()).upload(anyString(), any(), anyString());
    }

    // ---------- 404 存在しないメッセージ ----------

    @Test
    void attachingToMissingMessageReturns404() throws Exception {
        String token = login(OWNER);

        MockMultipartFile file = new MockMultipartFile(
                "file", "x.png", "image/png", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/messages/99999999/attachments")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    // ---------- auth ----------

    @Test
    void withoutTokenReturns401() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "x.png", "image/png", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/messages/1/attachments")
                        .file(file))
                .andExpect(status().isUnauthorized());
    }

    // ---------- helpers ----------

    private Long createChannelMessage(String userId, Long channelId, String body) {
        User user = userRepository.findByUserId(userId).orElseThrow();
        Channel channel = channelRepository.findByIdAndDeletedAtIsNull(channelId).orElseThrow();
        Message msg = new Message();
        msg.setWorkspace(channel.getWorkspace());
        msg.setChannel(channel);
        msg.setAuthor(user);
        msg.setBody(body);
        messageRepository.saveAndFlush(msg);
        return msg.getId();
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
