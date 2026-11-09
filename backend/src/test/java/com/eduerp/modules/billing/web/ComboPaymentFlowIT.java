package com.eduerp.modules.billing.web;

import com.eduerp.modules.courses.internal.model.ClassFixtures;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.AccessManagement;
import com.eduerp.modules.billing.BillingConstants;
import com.eduerp.modules.billing.dto.CreateComboInvoiceRequest;
import com.eduerp.modules.billing.dto.CreateComboRequest;
import com.eduerp.modules.billing.dto.RecordManualPaymentRequest;
import com.eduerp.modules.billing.internal.model.ComboDiscountTier;
import com.eduerp.modules.billing.internal.repository.ComboDiscountTierRepository;
import com.eduerp.modules.billing.internal.repository.InvoiceRepository;
import com.eduerp.modules.billing.usecase.MarkOverdueInvoices;
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

/**
 * Hai bất biến xuyên phân hệ, không có production code riêng:
 * <ul>
 *   <li>Review Focus #7 - {@code WithdrawEnrollment} không biết gì về combo, nên rút khỏi một khoá
 *       KHÔNG huỷ combo và KHÔNG tính lại giảm giá: công nợ đã phát sinh giữ nguyên.</li>
 *   <li>Review Focus #9 - mọi luồng thu tiền của Phase 3 chạy trên hoá đơn combo y như hoá đơn
 *       đơn-khoá, không một nhánh rẽ nào theo loại hoá đơn.</li>
 * </ul>
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class ComboPaymentFlowIT {

    private static final String PASSWORD = "Password123!";
    private static final String COMBOS = "/api/billing/combos";
    private static final String COMBO_INVOICES = COMBOS + "/invoices";
    private static final String INVOICES = "/api/billing/invoices";
    private static final BigDecimal MATHS_FEE = new BigDecimal("12000000");
    private static final BigDecimal ENGLISH_FEE = new BigDecimal("9000000");
    private static final LocalDate DUE_DATE = LocalDate.of(2027, 1, 31);
    private static final LocalDate PAST_DUE_DATE = LocalDate.of(2020, 1, 1);

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

    @Autowired
    InvoiceRepository invoices;

    @Autowired
    MarkOverdueInvoices markOverdueInvoices;

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private Cookie signIn(String email) throws Exception {
        var account = new Account(email, passwordEncoder.encode(PASSWORD), email, null);
        account.recordFirstLogin();
        accounts.save(account);
        access.assignRole(account.getId(), AccessConstants.RoleCodes.ADMIN);
        var result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, PASSWORD))))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return result.getResponse().getCookie(IdentityConstants.Cookies.ACCESS_TOKEN);
    }

    private record Fixture(UUID studentProfileId, String firstEnrollmentId, String secondEnrollmentId,
            String comboId) {
    }

    private UUID newClassId(UUID branchId, BigDecimal tuitionFee) {
        var course = courses.save(new Course("CPF-C-" + shortId(), "Khoá", null, 24, tuitionFee));
        var teacherId = accounts.save(new Account("cpf-gv-" + shortId() + "@eduerp.local", "hash", "GV", null))
                .getId();
        return classes.save(ClassFixtures.enrolling(course, "CPF-K-" + shortId(), branchId, teacherId, 20)).getId();
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

    /**
     * Một combo 2 khoá, giảm 10%: 21.000.000 → 18.900.000.
     *
     * <p>{@code deleteAll} trước khi seed: {@code min_course_count} là UNIQUE và mọi test trong class
     * {@code @SpringBootTest} này dùng chung một database, nên ghi lại cùng mốc 2 ở test thứ hai sẽ
     * vỡ constraint. Combo đã tạo ở test trước giữ nguyên % giảm vì đó là snapshot trên chính Combo.
     */
    private Fixture newCombo(Cookie admin) throws Exception {
        tiers.deleteAll();
        tiers.saveAndFlush(new ComboDiscountTier(2, new BigDecimal("10")));
        var branchId = branches.save(new Branch("CPF-" + shortId(), "Chi nhánh", null)).getId();
        var studentAccountId = accounts
                .save(new Account("cpf-hv-" + shortId() + "@eduerp.local", "hash", "HV", null)).getId();
        var studentProfileId = profiles.save(new StudentProfile(studentAccountId, null, null, null)).getId();
        var maths = enrol(admin, studentProfileId, newClassId(branchId, MATHS_FEE));
        var english = enrol(admin, studentProfileId, newClassId(branchId, ENGLISH_FEE));
        var result = mockMvc.perform(post(COMBOS).cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateComboRequest(studentProfileId,
                                List.of(UUID.fromString(maths), UUID.fromString(english))))))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        var comboId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
        return new Fixture(studentProfileId, maths, english, comboId);
    }

    private String reissueComboInvoice(Cookie admin, String comboId) throws Exception {
        var result = mockMvc.perform(post(COMBO_INVOICES).cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateComboInvoiceRequest(
                                UUID.fromString(comboId)))))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    /** Lập combo tự có hoá đơn. */
    private String comboInvoiceId(Cookie admin, String comboId) throws Exception {
        var detail = mockMvc.perform(get(COMBOS + "/" + comboId).cookie(admin)).andReturn();
        return objectMapper.readTree(detail.getResponse().getContentAsString()).get("invoices").get(0).get("id")
                .asText();
    }

    private org.springframework.test.web.servlet.MvcResult pay(Cookie admin, String invoiceId, String amount)
            throws Exception {
        return mockMvc.perform(post(INVOICES + "/" + invoiceId + "/manual-payment").cookie(admin)
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RecordManualPaymentRequest(new BigDecimal(amount)))))
                .andReturn();
    }

    /**
     * Rút một khoá khỏi combo chưa thu: giá đã giảm không còn đúng, nên combo được giải thể và khoá còn lại quay về hoá
     * đơn giá thường. Học viên không mang khoản nợ của lớp đã rút.
     */
    @Test
    void withdrawingFromAnUnpaidComboDissolvesItAndReissuesTheRemainingCourse() throws Exception {
        var admin = signIn("cpf-admin-1@eduerp.local");
        var fixture = newCombo(admin);

        var withdraw = mockMvc.perform(post("/api/enrollment/enrollments/" + fixture.firstEnrollmentId()
                + "/withdraw").cookie(admin).with(csrf())).andReturn();
        assertThat(withdraw.getResponse().getStatus()).isEqualTo(200);

        assertThat(mockMvc.perform(get(COMBOS + "/" + fixture.comboId()).cookie(admin)).andReturn().getResponse()
                .getStatus()).isEqualTo(404);
        var unpaid = mockMvc.perform(get(INVOICES).cookie(admin)
                .param("studentProfileId", fixture.studentProfileId().toString())
                .param("status", BillingConstants.InvoiceStatus.UNPAID.name())).andReturn();
        assertThat(objectMapper.readTree(unpaid.getResponse().getContentAsString()).get("items"))
                .singleElement().satisfies(invoice -> {
                    assertThat(invoice.get("enrollmentId").asText()).isEqualTo(fixture.secondEnrollmentId());
                    assertThat(invoice.get("amount").asLong()).isEqualTo(ENGLISH_FEE.longValue());
                });
    }

    /** Đã thu tiền thì giữ nguyên combo và số đã thu để đối soát hoàn phí bằng tay. */
    @Test
    void withdrawingAfterPaymentKeepsTheComboUntouched() throws Exception {
        var admin = signIn("cpf-admin-6@eduerp.local");
        var fixture = newCombo(admin);
        var invoiceId = comboInvoiceId(admin, fixture.comboId());
        assertThat(pay(admin, invoiceId, "18900000").getResponse().getStatus()).isEqualTo(200);

        var withdraw = mockMvc.perform(post("/api/enrollment/enrollments/" + fixture.firstEnrollmentId()
                + "/withdraw").cookie(admin).with(csrf())).andReturn();
        assertThat(withdraw.getResponse().getStatus()).isEqualTo(200);

        var detail = mockMvc.perform(get(COMBOS + "/" + fixture.comboId()).cookie(admin)).andReturn();
        var body = objectMapper.readTree(detail.getResponse().getContentAsString());
        assertThat(body.get("combo").get("totalDiscountedAmount").asLong()).isEqualTo(18_900_000L);
        assertThat(body.get("invoices").get(0).get("status").asText())
                .isEqualTo(BillingConstants.InvoiceStatus.PAID.name());
    }

    /** Thu đủ một lần: số gửi lên phải đúng phần còn lại, trả một phần bị từ chối. */
    @Test
    void refusesAPartialPaymentOnAComboInvoiceAndAcceptsTheFullAmount() throws Exception {
        var admin = signIn("cpf-admin-2@eduerp.local");
        var fixture = newCombo(admin);
        var invoiceId = comboInvoiceId(admin, fixture.comboId());

        var partial = pay(admin, invoiceId, "4000000");
        assertThat(partial.getResponse().getStatus()).isEqualTo(400);
        assertThat(partial.getResponse().getContentAsString()).contains("BILLING_INVALID_PAYMENT_AMOUNT");

        var settled = pay(admin, invoiceId, "18900000");
        var body = objectMapper.readTree(settled.getResponse().getContentAsString());
        assertThat(body.get("status").asText()).isEqualTo(BillingConstants.InvoiceStatus.PAID.name());
        assertThat(body.get("amountPaid").asLong()).isEqualTo(18_900_000L);
        assertThat(body.get("comboId").asText()).isEqualTo(fixture.comboId());

        var tooMuch = pay(admin, invoiceId, "1");
        assertThat(tooMuch.getResponse().getStatus()).isEqualTo(409);
        assertThat(tooMuch.getResponse().getContentAsString()).contains("BILLING_INVOICE_NOT_PAYABLE");
    }

    /** Huỷ hoá đơn combo chạy qua đúng {@code CancelInvoice}, rồi lập lại được đủ tổng sau giảm giá. */
    @Test
    void cancelsAComboInvoiceAndFreesItsInstallmentSlot() throws Exception {
        var admin = signIn("cpf-admin-3@eduerp.local");
        var fixture = newCombo(admin);
        var invoiceId = comboInvoiceId(admin, fixture.comboId());

        var cancel = mockMvc.perform(post(INVOICES + "/" + invoiceId + "/cancel").cookie(admin).with(csrf()))
                .andReturn();
        assertThat(cancel.getResponse().getStatus()).isEqualTo(200);

        // Đợt đã huỷ không tính vào tổng và không chiếm số đợt → phát hành lại cả tổng combo được.
        var reissuedId = reissueComboInvoice(admin, fixture.comboId());
        var reissued = mockMvc.perform(get(INVOICES + "/" + reissuedId).cookie(admin)).andReturn();
        assertThat(objectMapper.readTree(reissued.getResponse().getContentAsString()).get("invoice")
                .get("installmentNumber").asInt()).isEqualTo(1);
    }

    /** Review Focus #9: job quét quá hạn không lọc theo loại hoá đơn, nên hoá đơn combo quá hạn cũng
     * được đánh dấu OVERDUE như mọi hoá đơn khác. */
    @Test
    void marksAnOverdueComboInvoiceJustLikeAnyOtherInvoice() throws Exception {
        var admin = signIn("cpf-admin-4@eduerp.local");
        var fixture = newCombo(admin);
        var invoiceId = comboInvoiceId(admin, fixture.comboId());
        var issued = invoices.findById(UUID.fromString(invoiceId)).orElseThrow();
        issued.rescheduleDue(PAST_DUE_DATE);
        invoices.saveAndFlush(issued);

        markOverdueInvoices.execute();

        assertThat(invoices.findById(UUID.fromString(invoiceId)).orElseThrow().getStatus())
                .isEqualTo(BillingConstants.InvoiceStatus.OVERDUE);
        // OVERDUE vẫn thu được (nhãn nhắc nợ, không phải khoá sổ) - đúng BillingRules.isPayable.
        assertThat(pay(admin, invoiceId, "18900000").getResponse().getStatus()).isEqualTo(200);
    }

    /** Review Focus #9: danh sách hoá đơn lọc theo học viên trả về cả hoá đơn combo - học viên đó chỉ
     * có một công nợ duy nhất dù nó đến từ combo. */
    @Test
    void listsComboInvoicesAlongsideSingleCourseOnesForTheSameStudent() throws Exception {
        var admin = signIn("cpf-admin-5@eduerp.local");
        var fixture = newCombo(admin);
        var listed = mockMvc.perform(get(INVOICES).cookie(admin)
                .param("studentProfileId", fixture.studentProfileId().toString())
                .param("status", BillingConstants.InvoiceStatus.UNPAID.name())).andReturn();

        assertThat(listed.getResponse().getStatus()).isEqualTo(200);
        var items = objectMapper.readTree(listed.getResponse().getContentAsString()).get("items");
        assertThat(items).hasSize(1);
        assertThat(items.get(0).get("comboId").asText()).isEqualTo(fixture.comboId());
        assertThat(items.get(0).get("enrollmentId").isNull()).isTrue();
    }
}
