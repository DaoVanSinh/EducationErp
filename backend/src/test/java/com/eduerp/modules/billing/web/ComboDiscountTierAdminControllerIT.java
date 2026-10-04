package com.eduerp.modules.billing.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.AccessManagement;
import com.eduerp.modules.billing.dto.CreateComboDiscountTierRequest;
import com.eduerp.modules.billing.dto.UpdateComboDiscountTierRequest;
import com.eduerp.modules.identity.IdentityConstants;
import com.eduerp.modules.identity.dto.LoginRequest;
import com.eduerp.modules.identity.internal.model.Account;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redis.testcontainers.RedisContainer;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
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
class ComboDiscountTierAdminControllerIT {

    private static final String PASSWORD = "Password123!";
    private static final String TIERS = "/api/billing/combo-discount-tiers";

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

    private String createTier(Cookie admin, int minCourseCount, String discountPercent) throws Exception {
        var result = mockMvc.perform(post(TIERS).cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateComboDiscountTierRequest(
                                minCourseCount, new BigDecimal(discountPercent)))))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    @Test
    void createsATierThenListsIt() throws Exception {
        var admin = signIn("tier-admin-1@eduerp.local", AccessConstants.RoleCodes.ADMIN);

        createTier(admin, 2, "10");

        var list = mockMvc.perform(get(TIERS).cookie(admin)).andReturn();
        assertThat(list.getResponse().getStatus()).isEqualTo(200);
        var tiers = objectMapper.readTree(list.getResponse().getContentAsString());
        assertThat(tiers.isArray()).isTrue();
        assertThat(tiers).anySatisfy(tier -> {
            assertThat(tier.get("minCourseCount").asInt()).isEqualTo(2);
            assertThat(tier.get("active").asBoolean()).isTrue();
        });
    }

    /** "Xoá" một bậc = PATCH active=false. Không có endpoint DELETE nào trên controller này. */
    @Test
    void retiresATierByTurningItInactiveInsteadOfDeletingIt() throws Exception {
        var admin = signIn("tier-admin-2@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var tierId = createTier(admin, 4, "20");

        var patched = mockMvc.perform(patch(TIERS + "/" + tierId).cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateComboDiscountTierRequest(
                                new BigDecimal("22.50"), false))))
                .andReturn();

        assertThat(patched.getResponse().getStatus()).isEqualTo(200);
        var body = objectMapper.readTree(patched.getResponse().getContentAsString());
        assertThat(body.get("active").asBoolean()).isFalse();
        assertThat(body.get("discountPercent").asDouble()).isEqualTo(22.5);
        assertThat(body.get("minCourseCount").asInt()).isEqualTo(4);
    }

    @Test
    void refusesASecondTierForTheSameThreshold() throws Exception {
        var admin = signIn("tier-admin-3@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        createTier(admin, 6, "30");

        var duplicate = mockMvc.perform(post(TIERS).cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateComboDiscountTierRequest(
                                6, new BigDecimal("35")))))
                .andReturn();

        assertThat(duplicate.getResponse().getStatus()).isEqualTo(409);
        assertThat(duplicate.getResponse().getContentAsString())
                .contains("BILLING_COMBO_DISCOUNT_TIER_ALREADY_EXISTS");
    }

    @Test
    void refusesAnUnknownTierOnUpdate() throws Exception {
        var admin = signIn("tier-admin-4@eduerp.local", AccessConstants.RoleCodes.ADMIN);

        var result = mockMvc.perform(patch(TIERS + "/" + UUID.randomUUID()).cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateComboDiscountTierRequest(
                                new BigDecimal("10"), true))))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(result.getResponse().getContentAsString())
                .contains("BILLING_COMBO_DISCOUNT_TIER_NOT_FOUND");
    }

    /** minCourseCount < 2 vô nghĩa: combo tối thiểu 2 khoá, bậc mốc 1 sẽ không bao giờ được dùng. */
    @Test
    void refusesATierThresholdBelowTheMinimumComboSize() throws Exception {
        var admin = signIn("tier-admin-5@eduerp.local", AccessConstants.RoleCodes.ADMIN);

        var result = mockMvc.perform(post(TIERS).cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateComboDiscountTierRequest(
                                1, new BigDecimal("5")))))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void refusesAPercentAboveOneHundred() throws Exception {
        var admin = signIn("tier-admin-6@eduerp.local", AccessConstants.RoleCodes.ADMIN);

        var result = mockMvc.perform(post(TIERS).cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateComboDiscountTierRequest(
                                2, new BigDecimal("100.01")))))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
    }

    /** Cấu hình giá dùng chung nhóm quyền với người quản lý học phí (spec mục 8). */
    @Test
    void refusesEveryTierEndpointWithoutInvoicePermission() throws Exception {
        var admin = signIn("tier-admin-7@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var outsider = signIn("tier-outsider-1@eduerp.local", AccessConstants.RoleCodes.TEACHER);
        var tierId = createTier(admin, 7, "35");

        assertThat(mockMvc.perform(get(TIERS).cookie(outsider)).andReturn().getResponse().getStatus())
                .isEqualTo(403);
        assertThat(mockMvc.perform(patch(TIERS + "/" + tierId).cookie(outsider).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateComboDiscountTierRequest(
                                new BigDecimal("10"), true))))
                .andReturn().getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void requiresAuthentication() throws Exception {
        assertThat(mockMvc.perform(get(TIERS)).andReturn().getResponse().getStatus()).isEqualTo(401);
    }
}
