package com.eduerp.modules.billing.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.AccessManagement;
import com.eduerp.modules.billing.dto.CreateComboInvoiceRequest;
import com.eduerp.modules.billing.dto.CreateComboRequest;
import com.eduerp.modules.billing.internal.model.ComboDiscountTier;
import com.eduerp.modules.billing.internal.repository.ComboDiscountTierRepository;
import com.eduerp.modules.courses.internal.model.Class;
import com.eduerp.modules.courses.internal.model.Course;
import com.eduerp.modules.courses.internal.repository.ClassRepository;
import com.eduerp.modules.courses.internal.repository.CourseRepository;
import com.eduerp.modules.enrollment.dto.CreateEnrollmentRequest;
import com.eduerp.modules.identity.IdentityConstants;
import com.eduerp.modules.identity.dto.LoginRequest;
import com.eduerp.modules.identity.internal.model.Account;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import com.eduerp.modules.organization.internal.model.Branch;
import com.eduerp.modules.organization.internal.repository.BranchRepository;
import com.eduerp.modules.students.internal.model.StudentProfile;
import com.eduerp.modules.students.internal.repository.StudentProfileRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redis.testcontainers.RedisContainer;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class ComboAdminControllerIT {

    private static final String PASSWORD = "Password123!";
    private static final String COMBOS = "/api/billing/combos";
    private static final String COMBO_INVOICES = COMBOS + "/invoices";
    private static final BigDecimal MATHS_FEE = new BigDecimal("12000000");
    private static final BigDecimal ENGLISH_FEE = new BigDecimal("9000000");
    private static final LocalDate DUE_DATE = LocalDate.of(2027, 1, 31);

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
    PasswordEncoder passwordEncoder;

    @Autowired
    BranchRepository branches;

    @Autowired
    CourseRepository courses;

    @Autowired
    ClassRepository classes;

    @Autowired
    StudentProfileRepository profiles;

    @Autowired
    ComboDiscountTierRepository tiers;

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private Cookie signIn(String email, String roleCode) throws Exception {
        var account = new Account(email, passwordEncoder.encode(PASSWORD), email, null);
        account.recordFirstLogin();
        accounts.save(account);
        access.assignRole(account.getId(), roleCode);
        var result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, PASSWORD))))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return result.getResponse().getCookie(IdentityConstants.Cookies.ACCESS_TOKEN);
    }

    /** Một học viên, một chi nhánh, hai khoá - đúng hình dạng tối thiểu của một combo hợp lệ. Ghi
     * danh đi qua chính HTTP API của enrollment để test cũng chứng minh hai module ghép được. */
    private record ComboFixture(UUID studentProfileId, UUID branchId, String firstEnrollmentId,
            String secondEnrollmentId) {
    }

    private String enrol(Cookie admin, UUID studentProfileId, UUID classId) throws Exception {
        var result = mockMvc.perform(post("/api/enrollment/enrollments").cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateEnrollmentRequest(studentProfileId, classId))))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private UUID newClassId(UUID branchId, BigDecimal tuitionFee) {
        var course = courses.save(new Course("CMBW-C-" + shortId(), "Khoá", null, 24, tuitionFee));
        var teacherId = accounts.save(new Account("cmb-web-gv-" + shortId() + "@eduerp.local", "hash", "GV", null))
                .getId();
        return classes.save(new Class(course, "CMBW-K-" + shortId(), branchId, teacherId, 20)).getId();
    }

    private ComboFixture newTwoCourseFixture(Cookie admin) throws Exception {
        var branchId = branches.save(new Branch("CMBW-" + shortId(), "Chi nhánh", null)).getId();
        var studentAccountId = accounts
                .save(new Account("cmb-web-hv-" + shortId() + "@eduerp.local", "hash", "HV", null)).getId();
        var studentProfileId = profiles.save(new StudentProfile(studentAccountId, null, null, null)).getId();
        var maths = enrol(admin, studentProfileId, newClassId(branchId, MATHS_FEE));
        var english = enrol(admin, studentProfileId, newClassId(branchId, ENGLISH_FEE));
        return new ComboFixture(studentProfileId, branchId, maths, english);
    }

    /**
     * {@code min_course_count} là UNIQUE và mọi test trong class {@code @SpringBootTest} này dùng
     * CHUNG một database, nên seed lại cùng một mốc ở test thứ hai sẽ vỡ constraint - và nếu chỉ
     * "seed nếu chưa có" thì % giảm mà một test thấy lại phụ thuộc test nào chạy trước. Xoá sạch rồi
     * ghi đúng bộ bậc mà test này cần: mỗi test độc lập, không có thứ tự ngầm. Combo đã tạo ở test
     * trước không bị ảnh hưởng vì {@code Combo.discountPercent} là snapshot, không phải khoá ngoại.
     */
    private void resetTiersTo(int minCourseCount, String discountPercent) {
        tiers.deleteAll();
        tiers.saveAndFlush(new ComboDiscountTier(minCourseCount, new BigDecimal(discountPercent)));
    }

    private String createCombo(Cookie admin, ComboFixture fixture) throws Exception {
        var result = mockMvc.perform(post(COMBOS).cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateComboRequest(
                                fixture.studentProfileId(),
                                List.of(UUID.fromString(fixture.firstEnrollmentId()),
                                        UUID.fromString(fixture.secondEnrollmentId())),
                                DUE_DATE))))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    @Test
    void createsAComboThenIssuesTwoInstallmentsAndRefusesAnythingBeyondTheDiscountedTotal()
            throws Exception {
        var admin = signIn("cmb-admin-1@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        resetTiersTo(2, "15");
        var fixture = newTwoCourseFixture(admin);

        var comboId = createCombo(admin, fixture);

        var detail = mockMvc.perform(get(COMBOS + "/" + comboId).cookie(admin)).andReturn();
        var comboNode = objectMapper.readTree(detail.getResponse().getContentAsString()).get("combo");
        assertThat(comboNode.get("totalOriginalAmount").asLong()).isEqualTo(21_000_000L);
        assertThat(comboNode.get("totalDiscountedAmount").asLong()).isEqualTo(17_850_000L);
        assertThat(comboNode.get("courseCount").asInt()).isEqualTo(2);

        assertThat(issueComboInvoice(admin, comboId, "10000000").getResponse().getStatus()).isEqualTo(200);
        assertThat(issueComboInvoice(admin, comboId, "7850000").getResponse().getStatus()).isEqualTo(200);

        var tooMuch = issueComboInvoice(admin, comboId, "1");
        assertThat(tooMuch.getResponse().getStatus()).isEqualTo(409);
        assertThat(tooMuch.getResponse().getContentAsString())
                .contains("BILLING_INVOICE_AMOUNT_EXCEEDS_TUITION");
    }

    /** Hoá đơn combo trả về comboId và KHÔNG có enrollmentId/courseId - hợp đồng với Zod ở frontend. */
    @Test
    void comboInvoicesCarryTheComboIdAndNoEnrollmentOrCourse() throws Exception {
        var admin = signIn("cmb-admin-2@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        resetTiersTo(2, "10");
        var comboId = createCombo(admin, newTwoCourseFixture(admin));

        var body = issueComboInvoice(admin, comboId, "1000000").getResponse().getContentAsString();

        var invoice = objectMapper.readTree(body);
        assertThat(invoice.get("comboId").asText()).isEqualTo(comboId);
        assertThat(invoice.get("enrollmentId").isNull()).isTrue();
        assertThat(invoice.get("courseId").isNull()).isTrue();
        assertThat(invoice.get("installmentNumber").asInt()).isEqualTo(1);
    }

    /** Review Focus #6 qua HTTP: huỷ được khi chưa có hoá đơn, bị chặn 409 sau khi có hoá đơn. */
    @Test
    void cancelsAComboBeforeItsFirstInvoiceAndRefusesAfterwards() throws Exception {
        var admin = signIn("cmb-admin-3@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        resetTiersTo(2, "10");
        var fixture = newTwoCourseFixture(admin);
        var disposable = createCombo(admin, fixture);

        var cancel = mockMvc.perform(post(COMBOS + "/" + disposable + "/cancel").cookie(admin).with(csrf()))
                .andReturn();
        assertThat(cancel.getResponse().getStatus()).isEqualTo(200);
        assertThat(mockMvc.perform(get(COMBOS + "/" + disposable).cookie(admin)).andReturn()
                .getResponse().getStatus()).isEqualTo(404);

        // Xoá cứng giải phóng enrollment_id, nên gộp lại chính hai ghi danh đó phải được.
        var rebuilt = createCombo(admin, fixture);
        issueComboInvoice(admin, rebuilt, "1000000");

        var refused = mockMvc.perform(post(COMBOS + "/" + rebuilt + "/cancel").cookie(admin).with(csrf()))
                .andReturn();
        assertThat(refused.getResponse().getStatus()).isEqualTo(409);
        assertThat(refused.getResponse().getContentAsString()).contains("BILLING_COMBO_HAS_INVOICES");
    }

    /** Review Focus #1 qua HTTP: hai học viên khác nhau → 400, không phải 500 hay 200. */
    @Test
    void refusesAComboMixingTwoStudents() throws Exception {
        var admin = signIn("cmb-admin-4@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        resetTiersTo(2, "10");
        var mine = newTwoCourseFixture(admin);
        var someoneElse = newTwoCourseFixture(admin);

        var result = mockMvc.perform(post(COMBOS).cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateComboRequest(
                                mine.studentProfileId(),
                                List.of(UUID.fromString(mine.firstEnrollmentId()),
                                        UUID.fromString(someoneElse.firstEnrollmentId())),
                                DUE_DATE))))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(result.getResponse().getContentAsString()).contains("BILLING_COMBO_STUDENT_MISMATCH");
    }

    /** Review Focus #4 qua HTTP: chưa cấu hình bậc nào → 409, không âm thầm giảm 0%. */
    @Test
    void refusesAComboWhenNoDiscountTierIsConfigured() throws Exception {
        var admin = signIn("cmb-admin-5@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        // Rỗng hẳn: test này chứng minh "chưa cấu hình bậc nào" bị chặn, nên không được phụ thuộc
        // vào việc test nào chạy trước có seed bậc hay không.
        tiers.deleteAll();
        var fixture = newTwoCourseFixture(admin);

        var result = mockMvc.perform(post(COMBOS).cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateComboRequest(
                                fixture.studentProfileId(),
                                List.of(UUID.fromString(fixture.firstEnrollmentId()),
                                        UUID.fromString(fixture.secondEnrollmentId())),
                                DUE_DATE))))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(result.getResponse().getContentAsString())
                .contains("BILLING_COMBO_DISCOUNT_TIER_NOT_CONFIGURED");
    }

    /** Review Focus #3 qua HTTP: gộp lại một ghi danh đã nằm trong combo khác → 409 có errorCode. */
    @Test
    void refusesToReuseAnEnrollmentThatAlreadyBelongsToAnotherCombo() throws Exception {
        var admin = signIn("cmb-admin-6@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        resetTiersTo(2, "10");
        var fixture = newTwoCourseFixture(admin);
        createCombo(admin, fixture);
        // Khoá thứ ba, cùng học viên cùng chi nhánh - hợp lệ về mọi mặt TRỪ việc ghi danh thứ nhất
        // đã nằm trong combo vừa tạo.
        var thirdEnrollmentId = enrol(admin, fixture.studentProfileId(),
                newClassId(fixture.branchId(), MATHS_FEE));

        var result = mockMvc.perform(post(COMBOS).cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateComboRequest(
                                fixture.studentProfileId(),
                                List.of(UUID.fromString(fixture.firstEnrollmentId()),
                                        UUID.fromString(thirdEnrollmentId)),
                                DUE_DATE))))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(result.getResponse().getContentAsString())
                .contains("BILLING_ENROLLMENT_ALREADY_IN_COMBO");
    }

    @Test
    void filtersTheComboListByStudent() throws Exception {
        var admin = signIn("cmb-admin-7@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        resetTiersTo(2, "10");
        var fixture = newTwoCourseFixture(admin);
        createCombo(admin, fixture);

        var filtered = mockMvc.perform(get(COMBOS).cookie(admin)
                .param("studentProfileId", fixture.studentProfileId().toString())).andReturn();

        assertThat(filtered.getResponse().getStatus()).isEqualTo(200);
        assertThat(objectMapper.readTree(filtered.getResponse().getContentAsString()).get("totalItems").asInt())
                .isEqualTo(1);
    }

    /** RBAC dùng LẠI quyền INVOICE (spec mục 8): không có quyền đó thì mọi endpoint combo đều 403. */
    @Test
    void refusesEveryComboEndpointWithoutInvoicePermission() throws Exception {
        var admin = signIn("cmb-admin-8@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var outsider = signIn("cmb-outsider-1@eduerp.local", AccessConstants.RoleCodes.TEACHER);
        resetTiersTo(2, "10");
        var comboId = createCombo(admin, newTwoCourseFixture(admin));

        assertThat(mockMvc.perform(get(COMBOS).cookie(outsider)).andReturn().getResponse().getStatus())
                .isEqualTo(403);
        assertThat(mockMvc.perform(get(COMBOS + "/" + comboId).cookie(outsider)).andReturn()
                .getResponse().getStatus()).isEqualTo(403);
        assertThat(mockMvc.perform(post(COMBOS + "/" + comboId + "/cancel").cookie(outsider).with(csrf()))
                .andReturn().getResponse().getStatus()).isEqualTo(403);
        assertThat(mockMvc.perform(post(COMBO_INVOICES).cookie(outsider).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateComboInvoiceRequest(
                                UUID.fromString(comboId), new BigDecimal("1000000"), DUE_DATE))))
                .andReturn().getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void requiresAuthentication() throws Exception {
        assertThat(mockMvc.perform(get(COMBOS)).andReturn().getResponse().getStatus()).isEqualTo(401);
    }

    private org.springframework.test.web.servlet.MvcResult issueComboInvoice(Cookie admin, String comboId,
            String amount) throws Exception {
        return mockMvc.perform(post(COMBO_INVOICES).cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateComboInvoiceRequest(
                                UUID.fromString(comboId), new BigDecimal(amount), DUE_DATE))))
                .andReturn();
    }
}
