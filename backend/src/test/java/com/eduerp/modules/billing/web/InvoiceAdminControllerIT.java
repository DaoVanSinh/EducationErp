package com.eduerp.modules.billing.web;

import com.eduerp.modules.courses.internal.model.ClassFixtures;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.AccessManagement;
import com.eduerp.modules.billing.BillingConstants;
import com.eduerp.modules.billing.dto.CreateInvoiceRequest;
import com.eduerp.modules.billing.dto.RecordManualPaymentRequest;
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
class InvoiceAdminControllerIT {

    private static final String PASSWORD = "Password123!";
    private static final String INVOICES = "/api/billing/invoices";
    private static final BigDecimal TUITION = new BigDecimal("12000000");
    private static final BigDecimal HALF = new BigDecimal("6000000");
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

    /** Dựng một ghi danh ACTIVE qua chính HTTP API của enrollment - không chọc thẳng repository, để
     * test cũng chứng minh hai module ghép được với nhau. */
    private String newActiveEnrollmentId(Cookie admin, BigDecimal tuitionFee) throws Exception {
        var branchId = branches.save(new Branch("BILW-" + shortId(), "Chi nhánh", null)).getId();
        var course = courses.save(new Course("BILW-C-" + shortId(), "Khoá", null, 24, tuitionFee));
        var teacherId = accounts.save(new Account("bil-web-gv-" + shortId() + "@eduerp.local", "hash", "GV", null))
                .getId();
        var classId = classes.save(ClassFixtures.enrolling(course, "BILW-K-" + shortId(), branchId, teacherId, 20)).getId();
        var studentAccountId = accounts
                .save(new Account("bil-web-hv-" + shortId() + "@eduerp.local", "hash", "HV", null)).getId();
        var studentProfileId = profiles.save(new StudentProfile(studentAccountId, null, null, null)).getId();
        var result = mockMvc.perform(post("/api/enrollment/enrollments").cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateEnrollmentRequest(studentProfileId, classId))))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    /** Ghi danh tự có một hoá đơn. */
    private String invoiceOf(Cookie admin, String enrollmentId) throws Exception {
        var listed = mockMvc.perform(get(INVOICES).cookie(admin).param("enrollmentId", enrollmentId)
                .param("status", BillingConstants.InvoiceStatus.UNPAID.name())).andReturn();
        var items = objectMapper.readTree(listed.getResponse().getContentAsString()).get("items");
        assertThat(items).hasSize(1);
        return items.get(0).get("id").asText();
    }

    private org.springframework.test.web.servlet.MvcResult reissue(Cookie admin, String enrollmentId) throws Exception {
        return mockMvc.perform(post(INVOICES).cookie(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateInvoiceRequest(UUID.fromString(enrollmentId)))))
                .andReturn();
    }

    /** Mỗi ghi danh một hoá đơn đủ học phí, tự lập khi ghi danh; lập thêm bị chặn. */
    @Test
    void issuesTheFullTuitionOnEnrollmentThenRefusesAnotherInvoice() throws Exception {
        var admin = signIn("bil-admin-1@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var enrollmentId = newActiveEnrollmentId(admin, TUITION);

        var issued = mockMvc.perform(get(INVOICES + "/" + invoiceOf(admin, enrollmentId)).cookie(admin)).andReturn();
        var invoice = objectMapper.readTree(issued.getResponse().getContentAsString()).get("invoice");
        assertThat(invoice.get("amount").asLong()).isEqualTo(TUITION.longValue());
        assertThat(invoice.get("installmentNumber").asInt()).isEqualTo(1);

        var second = reissue(admin, enrollmentId);
        assertThat(second.getResponse().getStatus()).isEqualTo(409);
        assertThat(second.getResponse().getContentAsString()).contains("BILLING_ENROLLMENT_ALREADY_INVOICED");
    }

    @Test
    void refusesAnInvoiceForACourseWithoutATuitionFee() throws Exception {
        var admin = signIn("bil-admin-2@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var enrollmentId = newActiveEnrollmentId(admin, null);

        var result = reissue(admin, enrollmentId);

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(result.getResponse().getContentAsString()).contains("BILLING_COURSE_TUITION_NOT_CONFIGURED");
    }

    /** Thu đủ một lần: trả một phần bị từ chối, trả đúng phần còn lại thì xong. */
    @Test
    void refusesAPartialManualPaymentAndAcceptsTheFullAmount() throws Exception {
        var admin = signIn("bil-admin-3@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var enrollmentId = newActiveEnrollmentId(admin, TUITION);
        var invoiceId = invoiceOf(admin, enrollmentId);

        var partial = mockMvc.perform(post(INVOICES + "/" + invoiceId + "/manual-payment").cookie(admin)
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RecordManualPaymentRequest(new BigDecimal("2000000")))))
                .andReturn();
        assertThat(partial.getResponse().getStatus()).isEqualTo(400);
        assertThat(partial.getResponse().getContentAsString()).contains("BILLING_INVALID_PAYMENT_AMOUNT");

        var full = mockMvc.perform(post(INVOICES + "/" + invoiceId + "/manual-payment").cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RecordManualPaymentRequest(TUITION))))
                .andReturn();
        assertThat(objectMapper.readTree(full.getResponse().getContentAsString()).get("status").asText())
                .isEqualTo(BillingConstants.InvoiceStatus.PAID.name());

        var detail = mockMvc.perform(get(INVOICES + "/" + invoiceId).cookie(admin)).andReturn();
        var payments = objectMapper.readTree(detail.getResponse().getContentAsString()).get("payments");
        assertThat(payments).hasSize(1);
        assertThat(detail.getResponse().getContentAsString()).doesNotContain("gatewayTransactionId");
    }

    @Test
    void refusesAManualPaymentLargerThanTheRemainingBalance() throws Exception {
        var admin = signIn("bil-admin-4@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var enrollmentId = newActiveEnrollmentId(admin, TUITION);
        var invoiceId = invoiceOf(admin, enrollmentId);

        var result = mockMvc.perform(post(INVOICES + "/" + invoiceId + "/manual-payment").cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RecordManualPaymentRequest(TUITION.add(BigDecimal.ONE)))))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(result.getResponse().getContentAsString()).contains("BILLING_INVALID_PAYMENT_AMOUNT");
    }

    @Test
    void cancelsAnUnpaidInvoiceAndFreesItsInstallmentSlot() throws Exception {
        var admin = signIn("bil-admin-5@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var enrollmentId = newActiveEnrollmentId(admin, TUITION);
        var invoiceId = invoiceOf(admin, enrollmentId);

        var cancel = mockMvc.perform(post(INVOICES + "/" + invoiceId + "/cancel").cookie(admin).with(csrf()))
                .andReturn();
        assertThat(cancel.getResponse().getStatus()).isEqualTo(200);

        // Hoá đơn đã huỷ không chiếm chỗ và không tính vào tổng, nên phát hành lại cả học phí được.
        var created = reissue(admin, enrollmentId);
        assertThat(created.getResponse().getStatus()).isEqualTo(200);
        var reissuedId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();
        var reissued = mockMvc.perform(get(INVOICES + "/" + reissuedId).cookie(admin)).andReturn();
        assertThat(objectMapper.readTree(reissued.getResponse().getContentAsString()).get("invoice")
                .get("installmentNumber").asInt()).isEqualTo(1);
    }

    @Test
    void refusesToCancelAnInvoiceThatAlreadyReceivedMoney() throws Exception {
        var admin = signIn("bil-admin-6@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var enrollmentId = newActiveEnrollmentId(admin, TUITION);
        var invoiceId = invoiceOf(admin, enrollmentId);
        mockMvc.perform(post(INVOICES + "/" + invoiceId + "/manual-payment").cookie(admin).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RecordManualPaymentRequest(TUITION))));

        var cancel = mockMvc.perform(post(INVOICES + "/" + invoiceId + "/cancel").cookie(admin).with(csrf()))
                .andReturn();

        assertThat(cancel.getResponse().getStatus()).isEqualTo(409);
        assertThat(cancel.getResponse().getContentAsString()).contains("BILLING_INVOICE_NOT_PAYABLE");
    }

    @Test
    void filtersTheInvoiceListByEnrollmentAndStatus() throws Exception {
        var admin = signIn("bil-admin-7@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var enrollmentId = newActiveEnrollmentId(admin, TUITION);

        var filtered = mockMvc.perform(get(INVOICES).cookie(admin)
                .param("enrollmentId", enrollmentId)
                .param("status", BillingConstants.InvoiceStatus.UNPAID.name())).andReturn();

        assertThat(filtered.getResponse().getStatus()).isEqualTo(200);
        assertThat(objectMapper.readTree(filtered.getResponse().getContentAsString()).get("totalItems").asInt())
                .isEqualTo(1);
    }

    @Test
    void refusesEveryEndpointWithoutInvoicePermission() throws Exception {
        var admin = signIn("bil-admin-8@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var outsider = signIn("bil-outsider-1@eduerp.local", AccessConstants.RoleCodes.TEACHER);
        var enrollmentId = newActiveEnrollmentId(admin, TUITION);
        var invoiceId = invoiceOf(admin, enrollmentId);

        assertThat(mockMvc.perform(get(INVOICES).cookie(outsider)).andReturn().getResponse().getStatus())
                .isEqualTo(403);
        assertThat(mockMvc.perform(post(INVOICES + "/" + invoiceId + "/cancel").cookie(outsider).with(csrf()))
                .andReturn().getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void requiresAuthentication() throws Exception {
        assertThat(mockMvc.perform(get(INVOICES)).andReturn().getResponse().getStatus()).isEqualTo(401);
    }
}
