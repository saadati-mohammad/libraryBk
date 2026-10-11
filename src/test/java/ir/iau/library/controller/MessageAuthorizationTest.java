package ir.iau.library.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ir.iau.library.entity.FileAttachment;
import ir.iau.library.entity.Message;
import ir.iau.library.repository.FileAttachmentRepository;
import ir.iau.library.repository.MessageRepository;
import ir.iau.library.repository.StaffAccountRepository;
import ir.iau.library.entity.StaffAccount;
import ir.iau.library.entity.StaffRole;
import ir.iau.library.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Principal-based authorization (IDOR) tests for the chat/attachment surface.
 *
 * <p>The application ships a single configurable admin credential, but the JWT filter trusts
 * any token signed with the configured secret, so a second identity is produced by signing a
 * token for another username with the real {@link JwtService}. The REST layer must then refuse
 * to hand that identity someone else's messages or files.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MessageAuthorizationTest {

    private static final String ALICE = "authz_alice";
    private static final String BOB = "authz_bob";
    private static final String CAROL = "authz_carol";
    private static final String SECRET_BODY = "authz secret body";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private FileAttachmentRepository fileAttachmentRepository;

    @Autowired
    private StaffAccountRepository staffAccountRepository;

    @Autowired
    private JwtService jwtService;

    private long messageId;
    private long fileId;

    @BeforeEach
    void seedConversationAndAttachment() {
        // JWT subjects are resolved against the database on each request, so seed actual
        // staff identities instead of relying on arbitrary signed subjects.
        for (String username : new String[]{ALICE, BOB, CAROL}) {
            if (staffAccountRepository.existsByUsernameIgnoreCase(username)) continue;
            StaffAccount account = new StaffAccount();
            account.setUsername(username);
            account.setDisplayName(username);
            account.setPasswordHash("unused-test-hash");
            account.setRole(StaffRole.LIBRARIAN);
            account.setActive(true);
            staffAccountRepository.save(account);
        }
        Message message = messageRepository.save(Message.builder()
                .sender(ALICE)
                .senderFarsiTitle("Alice")
                .recipient(BOB)
                .recipientFarsiTitle("Bob")
                .subject("authz subject")
                .message(SECRET_BODY)
                .priority("normal")
                .messageStatus("SENT")
                .isActive(true)
                .dataState(1)
                .build());
        messageId = message.getId();

        FileAttachment attachment = fileAttachmentRepository.save(FileAttachment.builder()
                .fileName("authz-file.txt")
                .originalFileName("authz-file.txt")
                .contentType("text/plain")
                .fileSize(3L)
                .filePath("/tmp/authz-file-not-on-disk.txt")
                .uploadStatus("COMPLETED")
                .message(message)
                .build());
        fileId = attachment.getId();
    }

    /** Sibling identity: a token signed with the real configured secret for another subject. */
    private String tokenFor(String username) {
        return jwtService.generateToken(username, "ADMIN");
    }

    @Test
    void senderCanReadTheConversation() throws Exception {
        MvcResult res = mockMvc.perform(get("/api/messages/conversation")
                        .param("senderUsername", ALICE)
                        .param("recipientUsername", BOB)
                        .header("Authorization", "Bearer " + tokenFor(ALICE)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode data = objectMapper.readTree(res.getResponse().getContentAsString()).get("data");
        assertThat(data.get("content").get(0).get("message").asText()).isEqualTo(SECRET_BODY);
    }

    @Test
    void conversationOfOtherUsersIsNotReturnedEvenWhenSenderUsernameIsSupplied() throws Exception {
        // Carol asks for Alice's conversation; the principal must win over the parameter.
        MvcResult res = mockMvc.perform(get("/api/messages/conversation")
                        .param("senderUsername", ALICE)
                        .param("recipientUsername", BOB)
                        .header("Authorization", "Bearer " + tokenFor(CAROL)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode data = objectMapper.readTree(res.getResponse().getContentAsString()).get("data");
        assertThat(data.get("totalElements").asLong()).isZero();
        assertThat(res.getResponse().getContentAsString()).doesNotContain(SECRET_BODY);
    }

    @Test
    void nonParticipantCannotUpdateAMessage() throws Exception {
        MvcResult res = mockMvc.perform(put("/api/messages/" + messageId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hijacked\"}")
                        .header("Authorization", "Bearer " + tokenFor(CAROL)))
                .andExpect(status().isForbidden())
                .andReturn();

        assertDeniedEnvelope(res);
    }

    @Test
    void nonParticipantCannotDeleteAMessage() throws Exception {
        MvcResult res = mockMvc.perform(delete("/api/messages/" + messageId)
                        .header("Authorization", "Bearer " + tokenFor(CAROL)))
                .andExpect(status().isForbidden())
                .andReturn();

        assertDeniedEnvelope(res);
    }

    @Test
    void onlyTheRecipientMayMarkAMessageRead() throws Exception {
        // The sender is a participant but not the recipient.
        mockMvc.perform(post("/api/messages/" + messageId + "/mark-read")
                        .header("Authorization", "Bearer " + tokenFor(ALICE)))
                .andExpect(status().isForbidden());

        // The recipient may.
        mockMvc.perform(post("/api/messages/" + messageId + "/mark-read")
                        .header("Authorization", "Bearer " + tokenFor(BOB)))
                .andExpect(status().isOk());
    }

    @Test
    void searchOnlyReturnsMessagesTheCallerParticipatesIn() throws Exception {
        String outsiderBody = mockMvc.perform(get("/api/messages/search")
                        .param("query", SECRET_BODY)
                        .header("Authorization", "Bearer " + tokenFor(CAROL)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(outsiderBody).doesNotContain(SECRET_BODY);

        String participantBody = mockMvc.perform(get("/api/messages/search")
                        .param("query", SECRET_BODY)
                        .header("Authorization", "Bearer " + tokenFor(ALICE)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(participantBody).get("data");
        assertThat(data.get("totalElements").asLong()).isPositive();
    }

    @Test
    void attachmentEndpointsAreDeniedToNonParticipants() throws Exception {
        assertDeniedEnvelope(mockMvc.perform(get("/api/v1/files/info/" + fileId)
                        .header("Authorization", "Bearer " + tokenFor(CAROL)))
                .andExpect(status().isForbidden())
                .andReturn());

        assertDeniedEnvelope(mockMvc.perform(get("/api/v1/files/download/" + fileId)
                        .header("Authorization", "Bearer " + tokenFor(CAROL)))
                .andExpect(status().isForbidden())
                .andReturn());

        assertDeniedEnvelope(mockMvc.perform(delete("/api/v1/files/delete/" + fileId)
                        .header("Authorization", "Bearer " + tokenFor(CAROL)))
                .andExpect(status().isForbidden())
                .andReturn());

        assertDeniedEnvelope(mockMvc.perform(get("/api/v1/files/message/" + messageId)
                        .header("Authorization", "Bearer " + tokenFor(CAROL)))
                .andExpect(status().isForbidden())
                .andReturn());
    }

    @Test
    void unattachedOrphanAttachmentIsDeniedToEveryOrdinaryUser() throws Exception {
        FileAttachment orphan = fileAttachmentRepository.save(FileAttachment.builder()
                .fileName("orphan.txt")
                .originalFileName("orphan.txt")
                .contentType("text/plain")
                .fileSize(1L)
                .filePath("/tmp/orphan-not-on-disk.txt")
                .uploadStatus("COMPLETED")
                .build());

        assertDeniedEnvelope(mockMvc.perform(get("/api/v1/files/info/" + orphan.getId())
                        .header("Authorization", "Bearer " + tokenFor(ALICE)))
                .andExpect(status().isForbidden())
                .andReturn());
        assertDeniedEnvelope(mockMvc.perform(delete("/api/v1/files/delete/" + orphan.getId())
                        .header("Authorization", "Bearer " + tokenFor(BOB)))
                .andExpect(status().isForbidden())
                .andReturn());
    }

    @Test
    void attachmentsRemainAvailableToTheConversationParticipants() throws Exception {
        mockMvc.perform(get("/api/v1/files/info/" + fileId)
                        .header("Authorization", "Bearer " + tokenFor(ALICE)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/files/message/" + messageId)
                        .header("Authorization", "Bearer " + tokenFor(BOB)))
                .andExpect(status().isOk());
    }

    private void assertDeniedEnvelope(MvcResult res) throws Exception {
        JsonNode body = objectMapper.readTree(res.getResponse().getContentAsString());
        assertThat(body.get("success").asBoolean()).isFalse();
        assertThat(body.get("message").asText()).isNotBlank();
    }
}
