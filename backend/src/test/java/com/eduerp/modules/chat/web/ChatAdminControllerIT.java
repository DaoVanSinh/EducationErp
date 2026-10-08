package com.eduerp.modules.chat.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.eduerp.integrations.ai.AiChatClient;
import com.eduerp.integrations.ai.AiProviderException;
import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.AccessManagement;
import com.eduerp.modules.chat.ChatConstants;
import com.eduerp.modules.chat.dto.AskChatbotRequest;
import com.eduerp.modules.chat.dto.ChatTurnPayload;
import com.eduerp.modules.courses.internal.model.Course;
import com.eduerp.modules.courses.internal.repository.CourseRepository;
import com.eduerp.modules.identity.IdentityConstants;
import com.eduerp.modules.identity.dto.LoginRequest;
import com.eduerp.modules.identity.internal.model.Account;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redis.testcontainers.RedisContainer;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Toàn bộ Phân hệ 5 end-to-end qua HTTP thật với quyền thật đọc từ Redis. Đây là chỗ DUY NHẤT chứng
 * minh ba điều mà không tầng nào khác chứng minh được: học viên scope PERSONAL đi qua được
 * {@code @PreAuthorize} (quyết định kỹ thuật #1 của plan), giáo viên bị chặn, và một lỗi hạ tầng của
 * nhà cung cấp ra 503 có errorCode chứ không 500 trống.
 *
 * <p>{@code @MockitoBean AiChatClient} thay bean thật: KHÔNG test nào trong repo được gọi Anthropic
 * thật (Global Constraint - chưa có ANTHROPIC_API_KEY, và một test phụ thuộc mạng/tiền là một test
 * sai). Mock cũng cho phép đọc lại CHÍNH XÁC chuỗi system text đã được gửi đi, thứ mà một lệnh gọi
 * thật không bao giờ cho ta thấy.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class ChatAdminControllerIT {

    private static final String PASSWORD = "Password123!";
    private static final String CHAT_ENDPOINT = "/api/chat/messages";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Container
    @ServiceConnection
    static RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7"));

    @MockitoBean
    AiChatClient aiChatClient;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    AccountRepository accounts;

    @Autowired
    CourseRepository courses;

    @Autowired
    AccessManagement access;

    @Autowired
    PasswordEncoder passwordEncoder;

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private UUID newAccount(String emailPrefix, String fullName) {
        return accounts.save(new Account(emailPrefix + "-" + shortId() + "@eduerp.local",
                passwordEncoder.encode(PASSWORD), fullName, null)).getId();
    }

    private Cookie signIn(String emailPrefix, String fullName, String roleCode) throws Exception {
        var accountId = newAccount(emailPrefix, fullName);
        var account = accounts.findById(accountId).orElseThrow();
        account.recordFirstLogin();
        accounts.save(account);
        access.assignRole(accountId, roleCode);
        access.evictPermissionCache(accountId);
        var result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest(account.getEmail(), PASSWORD))))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return result.getResponse().getCookie(IdentityConstants.Cookies.ACCESS_TOKEN);
    }

    private MockHttpServletResponse ask(Cookie cookie, AskChatbotRequest request) throws Exception {
        return mockMvc.perform(post(CHAT_ENDPOINT)
                        .cookie(cookie).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn().getResponse();
    }

    private String captureSystemText() {
        var captor = ArgumentCaptor.forClass(String.class);
        verify(aiChatClient).reply(captor.capture(), any());
        return captor.getValue();
    }

    private Course saveCourse(String name, boolean active) {
        var course = new Course("CH-" + shortId(), name, "Mô tả của " + name, 24,
                new BigDecimal("7000000"));
        course.setActive(active);
        return courses.saveAndFlush(course);
    }

    /**
     * Quyết định kỹ thuật #1 của plan, chứng minh end-to-end: học viên chỉ có
     * {@code CHAT:CREATE:PERSONAL} (V27) và vẫn đi qua được {@code @PreAuthorize}. Nếu ai đó thêm
     * marker {@code @ORGANIZATION} vào {@code CREATE_CHAT} thì test này đỏ ngay.
     */
    @Test
    void allowsAStudentWhoOnlyHasPersonalScopedChatPermission() throws Exception {
        when(aiChatClient.reply(anyString(), any()))
                .thenReturn("Trung tâm đang mở khóa Tiếng Anh giao tiếp buổi tối.");
        var student = signIn("chat-hv", "Nguyễn Văn A", AccessConstants.RoleCodes.STUDENT);

        var response = ask(student, new AskChatbotRequest("có khóa tiếng Anh buổi tối không", List.of()));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(objectMapper.readTree(response.getContentAsString()).get("reply").asText())
                .isEqualTo("Trung tâm đang mở khóa Tiếng Anh giao tiếp buổi tối.");
    }

    /** Spec mục 1: admin được cấp quyền thêm để kiểm thử/hỗ trợ. */
    @Test
    void allowsAnAdmin() throws Exception {
        when(aiChatClient.reply(anyString(), any())).thenReturn("ok");
        var admin = signIn("chat-admin", "Quản trị", AccessConstants.RoleCodes.ADMIN);

        assertThat(ask(admin, new AskChatbotRequest("có khóa nào không", List.of())).getStatus())
                .isEqualTo(200);
    }

    /**
     * Review Focus #4: giáo viên đăng nhập hợp lệ nhưng KHÔNG có quyền CHAT nào (V27 dùng một group
     * riêng chỉ gắn cho STUDENT) nên bị chặn ngay ở cửa - và quan trọng nhất, KHÔNG một lệnh gọi API
     * trả phí nào xảy ra.
     */
    @Test
    void refusesATeacherEntirely() throws Exception {
        var teacher = signIn("chat-gv", "Cô Lan", AccessConstants.RoleCodes.TEACHER);

        assertThat(ask(teacher, new AskChatbotRequest("có khóa nào không", List.of())).getStatus())
                .isEqualTo(403);
        verifyNoInteractions(aiChatClient);
    }

    /**
     * Review Focus #3 end-to-end: khóa đã đóng KHỚP từ khoá nhưng không được lọt vào ngữ cảnh gửi cho
     * model. Đây là chỗ duy nhất kiểm được điều đó trên chuỗi thật DB → facade → prompt.
     */
    @Test
    void neverPutsAnInactiveCourseIntoTheSystemTextSentToTheProvider() throws Exception {
        var marker = shortId();
        saveCourse("Nhiếp ảnh " + marker + " đã đóng", false);
        saveCourse("Nhiếp ảnh " + marker + " đang mở", true);
        when(aiChatClient.reply(anyString(), any())).thenReturn("ok");
        var student = signIn("chat-hv-active", "Học viên", AccessConstants.RoleCodes.STUDENT);

        assertThat(ask(student, new AskChatbotRequest("có khóa nhiếp ảnh " + marker + " không",
                List.of())).getStatus()).isEqualTo(200);

        assertThat(captureSystemText())
                .contains("Nhiếp ảnh " + marker + " đang mở")
                .doesNotContain("Nhiếp ảnh " + marker + " đã đóng");
    }

    /** Review Focus #2 end-to-end: không khớp khóa nào vẫn gửi đi một ngữ cảnh có nghĩa, và vẫn trả
     * 200 - "chưa tìm thấy" là một câu trả lời hợp lệ, không phải một lỗi. */
    @Test
    void stillAnswersWhenNoCourseMatches() throws Exception {
        when(aiChatClient.reply(anyString(), any())).thenReturn("Chưa tìm thấy khóa phù hợp.");
        var student = signIn("chat-hv-empty", "Học viên", AccessConstants.RoleCodes.STUDENT);

        assertThat(ask(student, new AskChatbotRequest("có khóa lái tàu vũ trụ " + shortId() + " không",
                List.of())).getStatus()).isEqualTo(200);

        assertThat(captureSystemText()).isNotBlank().contains("Không tìm thấy khóa học nào");
    }

    /** Review Focus #1 end-to-end: câu thứ 11 trong một phút bị 429 có errorCode. */
    @Test
    void returnsTooManyRequestsOnTheEleventhMessageInAMinute() throws Exception {
        when(aiChatClient.reply(anyString(), any())).thenReturn("ok");
        var student = signIn("chat-hv-quota", "Học viên hỏi nhiều", AccessConstants.RoleCodes.STUDENT);

        IntStream.range(0, ChatConstants.Limits.MAX_MESSAGES_PER_MINUTE).forEach(index -> {
            try {
                assertThat(ask(student, new AskChatbotRequest("câu " + index, List.of())).getStatus())
                        .as("câu hỏi thứ " + (index + 1) + " phải được phép")
                        .isEqualTo(200);
            } catch (Exception failure) {
                throw new IllegalStateException(failure);
            }
        });

        var blocked = ask(student, new AskChatbotRequest("câu vượt hạn mức", List.of()));

        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getContentAsString()).contains("CHAT_TOO_MANY_MESSAGES");
    }

    /** Review Focus #6 end-to-end: trần độ dài là của BACKEND - một client gọi thẳng API vẫn bị chặn,
     * với đúng errorCode của bảng lỗi (spec mục 5), không phải VALIDATION_FAILED. */
    @Test
    void rejectsATooLongMessageWithAProblemDetail() throws Exception {
        var student = signIn("chat-hv-long", "Học viên", AccessConstants.RoleCodes.STUDENT);
        var tooLong = "a".repeat(ChatConstants.Limits.MAX_MESSAGE_LENGTH + 1);

        var response = ask(student, new AskChatbotRequest(tooLong, List.of()));

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains("CHAT_MESSAGE_TOO_LONG");
        verifyNoInteractions(aiChatClient);
    }

    /** Review Focus #10: câu hỏi rỗng cũng bị chặn, cùng errorCode. */
    @Test
    void rejectsABlankMessage() throws Exception {
        var student = signIn("chat-hv-blank", "Học viên", AccessConstants.RoleCodes.STUDENT);

        var response = ask(student, new AskChatbotRequest("   ", List.of()));

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains("CHAT_MESSAGE_TOO_LONG");
    }

    /**
     * Review Focus #5 end-to-end, và là bài học trực tiếp từ Critical #1 của Phân hệ 4: nhà cung cấp
     * hỏng (ở production hiện tại nghĩa là "chưa cấu hình ANTHROPIC_API_KEY" → 401) phải ra 503 CÓ
     * errorCode, không phải 500 trống.
     */
    @Test
    void reportsAProviderFailureAsServiceUnavailableWithAnErrorCode() throws Exception {
        when(aiChatClient.reply(anyString(), any()))
                .thenThrow(new AiProviderException("401 authentication_error", null));
        var student = signIn("chat-hv-503", "Học viên", AccessConstants.RoleCodes.STUDENT);

        var response = ask(student, new AskChatbotRequest("có khóa nào không", List.of()));

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString()).contains("CHAT_PROVIDER_UNAVAILABLE");
    }

    /** Một lượt lịch sử dài quá mức vẫn bị Bean Validation chặn ở biên (Review Focus #7, lớp thứ hai
     * bên cạnh việc cắt bớt số lượt). */
    @Test
    void rejectsAHistoryTurnLongerThanAMessageCouldEverBe() throws Exception {
        var student = signIn("chat-hv-hist", "Học viên", AccessConstants.RoleCodes.STUDENT);
        var oversizedTurn = new ChatTurnPayload(ChatConstants.TurnRole.USER,
                "a".repeat(ChatConstants.Limits.MAX_HISTORY_TURN_LENGTH + 1));

        var response = ask(student, new AskChatbotRequest("câu hỏi mới", List.of(oversizedTurn)));

        assertThat(response.getStatus()).isEqualTo(400);
        verifyNoInteractions(aiChatClient);
    }

    /**
     * Final review Critical #2: một câu trả lời CỦA CHÍNH CHATBOT, dài hơn MAX_MESSAGE_LENGTH (trần
     * của câu hỏi MỚI do người dùng gõ) nhưng vẫn trong tầm một câu trả lời thật ở max-tokens hiện
     * tại, phải được CHẤP NHẬN khi client gửi nó lại như một lượt lịch sử - nếu không, một câu trả
     * lời bình thường tự khoá mọi câu hỏi SAU đó trong phiên bằng 400 không bao giờ tự hồi phục được.
     */
    @Test
    void acceptsAHistoryTurnLongerThanANewMessageCouldBeButStillWithinTheHistoryCeiling() throws Exception {
        when(aiChatClient.reply(anyString(), any())).thenReturn("ok");
        var student = signIn("chat-hv-longreply", "Học viên", AccessConstants.RoleCodes.STUDENT);
        var longAssistantReply = new ChatTurnPayload(ChatConstants.TurnRole.ASSISTANT,
                "a".repeat(ChatConstants.Limits.MAX_MESSAGE_LENGTH + 500));

        var response = ask(student, new AskChatbotRequest("câu hỏi tiếp theo", List.of(longAssistantReply)));

        assertThat(response.getStatus()).isEqualTo(200);
    }

    /** Chưa đăng nhập thì không vào được - endpoint này tốn tiền thật mỗi lượt gọi. */
    @Test
    void refusesAnAnonymousCaller() throws Exception {
        var response = mockMvc.perform(post(CHAT_ENDPOINT).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AskChatbotRequest("có khóa nào không", List.of()))))
                .andReturn().getResponse();

        assertThat(response.getStatus()).isIn(401, 403);
        verifyNoInteractions(aiChatClient);
    }

    /** Spec mục 2: backend KHÔNG lưu gì - response chỉ có đúng một field `reply`, không có id hội
     * thoại, không có dấu vết nào gợi ý rằng có thứ gì được lưu lại. */
    @Test
    void returnsNothingButTheReply() throws Exception {
        when(aiChatClient.reply(anyString(), any())).thenReturn("ok");
        var student = signIn("chat-hv-shape", "Học viên", AccessConstants.RoleCodes.STUDENT);

        var body = objectMapper.readTree(
                ask(student, new AskChatbotRequest("có khóa nào không", List.of())).getContentAsString());

        assertThat(body.fieldNames()).toIterable().containsExactly("reply");
    }
}
