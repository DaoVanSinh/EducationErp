package com.eduerp.modules.billing.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.AccessManagement;
import com.eduerp.modules.billing.dto.CreateComboRequest;
import com.eduerp.modules.billing.dto.CreateGroupRegistrationRequest;
import com.eduerp.modules.billing.dto.CreateInvoiceRequest;
import com.eduerp.modules.billing.internal.model.ComboDiscountTier;
import com.eduerp.modules.billing.internal.repository.ComboDiscountTierRepository;
import com.eduerp.modules.courses.internal.model.ClassFixtures;
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
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** Hai người rủ nhau học chung một khoá: mỗi người tự đóng học phí của mình, trần đã giảm theo nhóm. */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class GroupRegistrationAdminControllerIT {

    private static final String PASSWORD = "Password123!";
    private static final String GROUPS = "/api/billing/group-registrations";
    private static final BigDecimal FEE = new BigDecimal("4500000");
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

    private record Pair(UUID classId, UUID branchId, String first, String second, UUID firstStudent) {
    }

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

    private UUID newStudent() {
        var accountId = accounts.save(new Account("grp-hv-" + shortId() + "@eduerp.local", "hash", "HV", null))
                .getId();
        return profiles.save(new StudentProfile(accountId, null, null, null)).getId();
    }

    private UUID newClass(UUID branchId) {
        var course = courses.save(new Course("GRP-C-" + shortId(), "Khoá nhóm", null, 24, FEE));
        var teacherId = accounts.save(new Account("grp-gv-" + shortId() + "@eduerp.local", "hash", "GV", null))
                .getId();
        return classes.save(ClassFixtures.inProgress(course, "GRP-K-" + shortId(), branchId, teacherId, 20)).getId();
    }

    private String enrol(Cookie admin, UUID studentProfileId, UUID classId) throws Exception {
        var result = postJson(admin, "/api/enrollment/enrollments",
                new CreateEnrollmentRequest(studentProfileId, classId));
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private Pair twoFriendsInOneClass(Cookie admin) throws Exception {
        var branchId = branches.save(new Branch("GRP-" + shortId(), "Chi nhánh", null)).getId();
        var classId = newClass(branchId);
        var firstStudent = newStudent();
        return new Pair(classId, branchId, enrol(admin, firstStudent, classId), enrol(admin, newStudent(), classId),
                firstStudent);
    }

    private MvcResult postJson(Cookie admin, String path, Object body) throws Exception {
        return mockMvc.perform(post(path).cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andReturn();
    }

    private MvcResult createGroup(Cookie admin, String... enrollmentIds) throws Exception {
        var ids = java.util.Arrays.stream(enrollmentIds).map(UUID::fromString).toList();
        return postJson(admin, GROUPS, new CreateGroupRegistrationRequest(ids));
    }

    private MvcResult invoice(Cookie admin, String enrollmentId, String amount) throws Exception {
        return postJson(admin, "/api/billing/invoices",
                new CreateInvoiceRequest(UUID.fromString(enrollmentId), new BigDecimal(amount), DUE_DATE));
    }

    private void resetTiersTo(int minCount, String discountPercent) {
        tiers.deleteAll();
        tiers.saveAndFlush(new ComboDiscountTier(minCount, new BigDecimal(discountPercent)));
    }

    @Test
    void eachFriendPaysTheirOwnInvoicesCappedAtTheGroupTuition() throws Exception {
        var admin = signIn("grp-admin-1@eduerp.local");
        resetTiersTo(2, "10");
        var pair = twoFriendsInOneClass(admin);

        var created = createGroup(admin, pair.first(), pair.second());
        assertThat(created.getResponse().getStatus()).isEqualTo(200);
        var body = objectMapper.readTree(created.getResponse().getContentAsString());
        assertThat(body.get("members")).hasSize(2);
        assertThat(body.get("members").get(0).get("discountedTuitionFee").asLong()).isEqualTo(4_050_000L);

        assertThat(invoice(admin, pair.first(), "4500000").getResponse().getStatus()).isEqualTo(409);
        assertThat(invoice(admin, pair.first(), "4050000").getResponse().getStatus()).isEqualTo(200);
        assertThat(invoice(admin, pair.second(), "2000000").getResponse().getStatus()).isEqualTo(200);

        var listed = mockMvc.perform(get(GROUPS).cookie(admin)).andReturn();
        assertThat(listed.getResponse().getContentAsString()).contains(body.get("id").asText());
    }

    @Test
    void refusesToCancelAGroupOnceAMemberHasBeenInvoicedButCancelsAFreshOne() throws Exception {
        var admin = signIn("grp-admin-2@eduerp.local");
        resetTiersTo(2, "10");
        var invoicedPair = twoFriendsInOneClass(admin);
        var invoicedGroup = objectMapper.readTree(createGroup(admin, invoicedPair.first(), invoicedPair.second())
                .getResponse().getContentAsString()).get("id").asText();
        invoice(admin, invoicedPair.first(), "1000000");

        var refused = postJson(admin, GROUPS + "/" + invoicedGroup + "/cancel", null);
        assertThat(refused.getResponse().getStatus()).isEqualTo(409);
        assertThat(refused.getResponse().getContentAsString()).contains("BILLING_GROUP_HAS_INVOICES");

        var freshPair = twoFriendsInOneClass(admin);
        var freshGroup = objectMapper.readTree(createGroup(admin, freshPair.first(), freshPair.second())
                .getResponse().getContentAsString()).get("id").asText();
        assertThat(postJson(admin, GROUPS + "/" + freshGroup + "/cancel", null).getResponse().getStatus())
                .isEqualTo(200);
        // Huỷ xong, trần trở về học phí niêm yết.
        assertThat(invoice(admin, freshPair.first(), "4500000").getResponse().getStatus()).isEqualTo(200);
    }

    /** Combo và nhóm loại trừ nhau, và ghi danh trong combo không được phát hành hoá đơn lẻ. */
    @Test
    void anEnrollmentInAGroupCannotJoinAComboAndAComboEnrollmentCannotBeInvoicedAlone() throws Exception {
        var admin = signIn("grp-admin-3@eduerp.local");
        resetTiersTo(2, "10");
        var pair = twoFriendsInOneClass(admin);
        createGroup(admin, pair.first(), pair.second());
        var secondCourse = enrol(admin, pair.firstStudent(), newClass(pair.branchId()));

        var combo = postJson(admin, "/api/billing/combos", new CreateComboRequest(pair.firstStudent(),
                List.of(UUID.fromString(pair.first()), UUID.fromString(secondCourse)), DUE_DATE));
        assertThat(combo.getResponse().getStatus()).isEqualTo(409);
        assertThat(combo.getResponse().getContentAsString()).contains("BILLING_ENROLLMENT_ALREADY_DISCOUNTED");

        var thirdCourse = enrol(admin, pair.firstStudent(), newClass(pair.branchId()));
        assertThat(postJson(admin, "/api/billing/combos", new CreateComboRequest(pair.firstStudent(),
                List.of(UUID.fromString(secondCourse), UUID.fromString(thirdCourse)), DUE_DATE))
                .getResponse().getStatus()).isEqualTo(200);
        var single = invoice(admin, secondCourse, "1000000");
        assertThat(single.getResponse().getStatus()).isEqualTo(409);
        assertThat(single.getResponse().getContentAsString()).contains("BILLING_ENROLLMENT_BILLED_BY_COMBO");
    }
}
