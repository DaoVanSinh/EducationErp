package com.eduerp.modules.teachers.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.AccessManagement;
import com.eduerp.modules.identity.internal.model.Account;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import com.eduerp.modules.organization.internal.model.Branch;
import com.eduerp.modules.organization.internal.repository.BranchRepository;
import com.eduerp.modules.teachers.TeachersConstants.PayType;
import com.eduerp.modules.teachers.TeachersManagement;
import com.eduerp.modules.teachers.dto.AllowanceItem;
import com.eduerp.modules.teachers.dto.CreateTeacherRequest;
import com.eduerp.modules.teachers.dto.TeacherPayTermsRequest;
import com.eduerp.modules.teachers.dto.UpdateTeacherProfileRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redis.testcontainers.RedisContainer;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@org.springframework.test.context.TestPropertySource(properties = "management.health.mail.enabled=false")
class TeacherAdminControllerIT {

    private static final String PASSWORD = "Password123!";
    private static final String PROFILES = "/api/teachers/profiles";
    private static final LocalDate START = LocalDate.of(2026, 1, 1);
    private static final TeacherPayTermsRequest HOURLY_TERMS = new TeacherPayTermsRequest(PayType.HOURLY, null,
            new BigDecimal("250000"), START, null, 0, List.of());

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Container
    @ServiceConnection
    static RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7"));

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    AccountRepository accounts;

    @Autowired
    AccessManagement access;

    @Autowired
    TeachersManagement teachers;

    @Autowired
    BranchRepository branches;

    @Autowired
    PasswordEncoder passwordEncoder;

    @MockBean
    JavaMailSender mailSender;

    private UUID branchId;

    @BeforeEach
    void mockMailSenderAndSeedABranch() {
        given(mailSender.createMimeMessage()).willReturn(new MimeMessage((Session) null));
        branchId = branches.save(new Branch("TC-" + UUID.randomUUID().toString().substring(0, 8),
                "Chi nhánh giáo viên", null)).getId();
    }

    private Cookie signIn(String email, String roleCode) throws Exception {
        var account = new Account(email, passwordEncoder.encode(PASSWORD), email, null);
        account.recordFirstLogin();
        accounts.save(account);
        access.assignRole(account.getId(), roleCode);
        var result = mockMvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
                .andReturn();
        return result.getResponse().getCookie("access_token");
    }

    private int postTeacher(Cookie admin, Object request) throws Exception {
        return mockMvc.perform(post(PROFILES).cookie(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))).andReturn().getResponse().getStatus();
    }

    private UUID createTeacher(Cookie admin, CreateTeacherRequest request) throws Exception {
        var result = mockMvc.perform(post(PROFILES).cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return objectMapper.readValue(result.getResponse().getContentAsString(), UUID.class);
    }

    private CreateTeacherRequest teacher(String email, String name, TeacherPayTermsRequest terms) {
        return new CreateTeacherRequest(email, name, branchId, List.of("Toán"), null, null, terms);
    }

    @Test
    void createsAccountProfileAndPayTermsInOneStep() throws Exception {
        var admin = signIn("teacher-profile-admin@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var terms = new TeacherPayTermsRequest(PayType.MONTHLY, new BigDecimal("15000000"), null, START,
                START.plusMonths(2), 1, List.of(new AllowanceItem("Ăn trưa", new BigDecimal("730000"))));

        var profileId = createTeacher(admin, new CreateTeacherRequest("teacher-one-step@eduerp.local", "Cô Lan",
                branchId, List.of("Tiếng Anh", "IELTS"), "0900000000", "Giáo viên IELTS 8.0", terms));

        var account = accounts.findByTypedEmail("teacher-one-step@eduerp.local").orElseThrow();
        assertThat(access.grants(account.getId(), AccessConstants.Resources.TEACHING, AccessConstants.Actions.READ))
                .isTrue();
        assertThat(teachers.teacherProfileExists(account.getId())).isTrue();

        var listResult = mockMvc.perform(get(PROFILES).cookie(admin)).andReturn();
        assertThat(listResult.getResponse().getContentAsString())
                .contains("teacher-one-step@eduerp.local", "IELTS", "\"payConfigured\":true");

        var pay = objectMapper.readTree(mockMvc.perform(get(PROFILES + "/" + profileId + "/pay").cookie(admin))
                .andReturn().getResponse().getContentAsString());
        assertThat(pay.get("payType").asText()).isEqualTo("MONTHLY");
        assertThat(pay.get("monthlySalary").asDouble()).isEqualTo(15000000.0);
        assertThat(pay.get("dependentCount").asInt()).isEqualTo(1);
        assertThat(pay.get("allowances").get(0).get("name").asText()).isEqualTo("Ăn trưa");

        var facadeTerms = teachers.listPayTerms().stream()
                .filter(payTerms -> payTerms.accountId().equals(account.getId())).findFirst().orElseThrow();
        assertThat(facadeTerms.allowancesTotal()).isEqualByComparingTo("730000");
    }

    /** Mức lương không lộ qua API hồ sơ, và cần quyền lương để đọc riêng. */
    @Test
    void payIsHiddenFromProfileResponsesAndFromThoseWithoutPayrollPermission() throws Exception {
        var admin = signIn("teacher-profile-admin-6@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var outsider = signIn("teacher-profile-outsider@eduerp.local", AccessConstants.RoleCodes.TEACHER);
        var profileId = createTeacher(admin, teacher("teacher-pay-hidden@eduerp.local", "Cô Mai", HOURLY_TERMS));

        var profile = mockMvc.perform(get(PROFILES + "/" + profileId).cookie(admin)).andReturn();
        assertThat(profile.getResponse().getContentAsString()).doesNotContain("hourlyRate", "250000");

        var pay = mockMvc.perform(get(PROFILES + "/" + profileId + "/pay").cookie(outsider)).andReturn();
        assertThat(pay.getResponse().getStatus()).isEqualTo(403);

        var update = mockMvc.perform(put(PROFILES + "/" + profileId + "/pay").cookie(outsider).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(HOURLY_TERMS)))
                .andReturn();
        assertThat(update.getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void updatingPayChangesTheTerms() throws Exception {
        var admin = signIn("teacher-profile-admin-7@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var profileId = createTeacher(admin, teacher("teacher-pay-change@eduerp.local", "Thầy Bình", HOURLY_TERMS));
        var newTerms = new TeacherPayTermsRequest(PayType.MONTHLY, new BigDecimal("12000000"), null, START, null, 0,
                List.of());

        var result = mockMvc.perform(put(PROFILES + "/" + profileId + "/pay").cookie(admin).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(newTerms))).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        var pay = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(pay.get("payType").asText()).isEqualTo("MONTHLY");
        assertThat(pay.get("hourlyRate").isNull()).isTrue();
    }

    @Test
    void refusesATeacherWithoutBranchSubjectsOrValidPayTerms() throws Exception {
        var admin = signIn("teacher-profile-admin-8@eduerp.local", AccessConstants.RoleCodes.ADMIN);

        assertThat(postTeacher(admin, new CreateTeacherRequest("no-branch@eduerp.local", "A", null, List.of("Toán"),
                null, null, HOURLY_TERMS))).isEqualTo(400);
        assertThat(postTeacher(admin, new CreateTeacherRequest("no-subject@eduerp.local", "B", branchId, List.of(),
                null, null, HOURLY_TERMS))).isEqualTo(400);
        assertThat(postTeacher(admin, new CreateTeacherRequest("no-pay@eduerp.local", "C", branchId, List.of("Toán"),
                null, null, null))).isEqualTo(400);
        assertThat(postTeacher(admin, teacher("bad-pay@eduerp.local", "D", new TeacherPayTermsRequest(PayType.MONTHLY,
                null, null, START, null, 0, List.of())))).isEqualTo(400);

        assertThat(accounts.findByTypedEmail("bad-pay@eduerp.local")).as("lỗi lương không để lại tài khoản").isEmpty();
    }

    /** Giáo viên nghỉ: ngày ngừng là ngày làm việc cuối khi tính lương, mở lại thì xoá mốc đó. */
    @Test
    void deactivatingSetsTheLastWorkDateAndReactivatingClearsIt() throws Exception {
        var admin = signIn("teacher-profile-admin-3@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var email = "teacher-profile-2@eduerp.local";
        var profileId = createTeacher(admin, teacher(email, "Thầy Nam", HOURLY_TERMS));
        var accountId = accounts.findByTypedEmail(email).orElseThrow().getId();

        var updateResult = mockMvc.perform(patch(PROFILES + "/" + profileId).cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateTeacherProfileRequest(List.of("Toán", "Lý"), "0911111111", "Cập nhật"))))
                .andReturn();
        assertThat(updateResult.getResponse().getStatus()).isEqualTo(200);

        var getResult = mockMvc.perform(get(PROFILES + "/" + profileId).cookie(admin)).andReturn();
        assertThat(getResult.getResponse().getContentAsString()).contains("\"active\":true", "Lý");
        // Sửa hồ sơ không còn đổi trạng thái làm việc: cho nghỉ là thao tác riêng, có ngày nghỉ.
        assertThat(lastWorkDateOf(accountId)).isNull();
    }

    private LocalDate lastWorkDateOf(UUID accountId) {
        return teachers.listPayTerms().stream().filter(terms -> terms.accountId().equals(accountId)).findFirst()
                .orElseThrow().lastWorkDate();
    }

    @Test
    void getsASingleTeacherProfileById() throws Exception {
        var admin = signIn("teacher-profile-admin-4@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var profileId = createTeacher(admin, new CreateTeacherRequest("teacher-profile-3@eduerp.local", "Cô Hoa",
                branchId, List.of("Hoá"), "0922222222", "Mô tả", HOURLY_TERMS));

        var result = mockMvc.perform(get(PROFILES + "/" + profileId).cookie(admin)).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getContentAsString())
                .contains("teacher-profile-3@eduerp.local", "Hoá");
    }

    @Test
    void returns404ForAnUnknownTeacherProfileId() throws Exception {
        var admin = signIn("teacher-profile-admin-5@eduerp.local", AccessConstants.RoleCodes.ADMIN);

        var result = mockMvc.perform(get(PROFILES + "/" + UUID.randomUUID()).cookie(admin)).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(result.getResponse().getContentAsString()).contains("TEACHERS_PROFILE_NOT_FOUND");
    }
}
