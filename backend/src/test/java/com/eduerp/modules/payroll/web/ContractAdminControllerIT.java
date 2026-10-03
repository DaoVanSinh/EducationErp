package com.eduerp.modules.payroll.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.eduerp.integrations.storage.MinioTestImage;
import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.AccessManagement;
import com.eduerp.modules.audit.AuditConstants;
import com.eduerp.modules.audit.AuditManagement;
import com.eduerp.modules.identity.IdentityConstants;
import com.eduerp.modules.identity.dto.LoginRequest;
import com.eduerp.modules.identity.internal.model.Account;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import com.eduerp.modules.payroll.PayrollConstants;
import com.eduerp.modules.payroll.dto.CreateContractRequest;
import com.eduerp.modules.payroll.internal.repository.EmploymentContractRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redis.testcontainers.RedisContainer;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class ContractAdminControllerIT {

    private static final String PASSWORD = "Password123!";
    private static final String BUCKET = "eduerp-test";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Container
    @ServiceConnection
    static RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7"));

    // Tag cố định như Task 8 đã xác nhận (Docker Hub từ chối pull tag khác, dùng tag đã có sẵn trong
    // cache local).
    @Container
    static MinIOContainer minio = new MinIOContainer(MinioTestImage.NAME);

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("storage.endpoint", minio::getS3URL);
        registry.add("storage.region", () -> "us-east-1");
        registry.add("storage.bucket", () -> BUCKET);
        registry.add("storage.access-key", minio::getUserName);
        registry.add("storage.secret-key", minio::getPassword);
    }

    // Testcontainers' JUnit5 extension khởi động mọi field @Container TRƯỚC khi gọi
    // @DynamicPropertySource (cần thiết để minio.getS3URL() có giá trị thật) - nên tại thời điểm
    // @BeforeAll chạy, container và bucket đều có thể tạo được, container đã start sẵn.
    @org.junit.jupiter.api.BeforeAll
    static void createBucket() {
        var client = S3Client.builder()
                .region(Region.US_EAST_1)
                .endpointOverride(URI.create(minio.getS3URL()))
                .forcePathStyle(true)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(minio.getUserName(), minio.getPassword())))
                .build();
        client.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    AccountRepository accounts;

    @Autowired
    EmploymentContractRepository contracts;

    @Autowired
    AccessManagement access;

    @Autowired
    AuditManagement audit;

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

    private UUID newEmployeeAccount(String email) {
        var account = new Account(email, passwordEncoder.encode(PASSWORD), email, null);
        account.recordFirstLogin();
        return accounts.save(account).getId();
    }

    @Test
    void createsThenListsAnOfficialContractAndAuditsIt() throws Exception {
        var admin = signIn("payroll-admin-1@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var employeeId = newEmployeeAccount("employee-1@eduerp.local");
        var request = new CreateContractRequest(employeeId, PayrollConstants.ContractType.OFFICIAL,
                new BigDecimal("10000000"), null, null, null, LocalDate.of(2026, 1, 1), List.of());

        var createResult = mockMvc.perform(multipart("/api/payroll/contracts")
                        .file(new MockMultipartFile("request", "", MediaType.APPLICATION_JSON_VALUE,
                                objectMapper.writeValueAsBytes(request)))
                        .cookie(admin).with(csrf()))
                .andReturn();
        assertThat(createResult.getResponse().getStatus()).isEqualTo(200);
        var contractId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

        var listResult = mockMvc.perform(get("/api/payroll/contracts").cookie(admin)).andReturn();
        assertThat(listResult.getResponse().getStatus()).isEqualTo(200);
        assertThat(listResult.getResponse().getContentAsString()).contains("employee-1@eduerp.local");

        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            var entityIds = audit.recentActions(AuditConstants.EntityTypes.CONTRACT,
                    AuditConstants.Actions.CONTRACT_CREATE, 20).stream().map(a -> a.entityId()).toList();
            assertThat(entityIds).contains(contractId);
        });
    }

    @Test
    void uploadsAndDownloadsTheContractFile() throws Exception {
        var admin = signIn("payroll-admin-2@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var employeeId = newEmployeeAccount("employee-2@eduerp.local");
        var request = new CreateContractRequest(employeeId, PayrollConstants.ContractType.OFFICIAL,
                new BigDecimal("8000000"), null, null, null, LocalDate.of(2026, 1, 1), List.of());

        var createResult = mockMvc.perform(multipart("/api/payroll/contracts")
                        .file(new MockMultipartFile("request", "", MediaType.APPLICATION_JSON_VALUE,
                                objectMapper.writeValueAsBytes(request)))
                        .file(new MockMultipartFile("file", "contract.pdf", "application/pdf", "nội dung pdf".getBytes()))
                        .cookie(admin).with(csrf()))
                .andReturn();
        assertThat(createResult.getResponse().getStatus()).isEqualTo(200);
        var contractId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

        var downloadResult = mockMvc.perform(get("/api/payroll/contracts/" + contractId + "/file").cookie(admin))
                .andReturn();
        assertThat(downloadResult.getResponse().getStatus()).isEqualTo(200);
        assertThat(downloadResult.getResponse().getContentAsByteArray()).isEqualTo("nội dung pdf".getBytes());
        // Review finding Important #4: thiếu header này thì trình duyệt lưu file không có tên/đuôi gốc.
        assertThat(downloadResult.getResponse().getHeader("Content-Disposition")).contains("contract.pdf");
    }

    /** Review Focus #3 ở tầng HTTP. */
    @Test
    void rejectsASecondActiveContractForTheSameAccountAtHttpLevel() throws Exception {
        var admin = signIn("payroll-admin-3@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var employeeId = newEmployeeAccount("employee-3@eduerp.local");
        var firstRequest = new CreateContractRequest(employeeId, PayrollConstants.ContractType.OFFICIAL,
                new BigDecimal("9000000"), null, null, null, LocalDate.of(2026, 1, 1), List.of());
        mockMvc.perform(multipart("/api/payroll/contracts")
                .file(new MockMultipartFile("request", "", MediaType.APPLICATION_JSON_VALUE,
                        objectMapper.writeValueAsBytes(firstRequest)))
                .cookie(admin).with(csrf()));

        var secondRequest = new CreateContractRequest(employeeId, PayrollConstants.ContractType.COLLABORATOR, null,
                new BigDecimal("150000"), null, null, LocalDate.of(2026, 2, 1), List.of());
        var result = mockMvc.perform(multipart("/api/payroll/contracts")
                        .file(new MockMultipartFile("request", "", MediaType.APPLICATION_JSON_VALUE,
                                objectMapper.writeValueAsBytes(secondRequest)))
                        .cookie(admin).with(csrf()))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(result.getResponse().getContentAsString()).contains("PAYROLL_CONTRACT_ALREADY_ACTIVE");
    }

    @Test
    void terminatesAContractAndAuditsIt() throws Exception {
        var admin = signIn("payroll-admin-4@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var employeeId = newEmployeeAccount("employee-4@eduerp.local");
        var request = new CreateContractRequest(employeeId, PayrollConstants.ContractType.OFFICIAL,
                new BigDecimal("7000000"), null, null, null, LocalDate.of(2026, 1, 1), List.of());
        var createResult = mockMvc.perform(multipart("/api/payroll/contracts")
                        .file(new MockMultipartFile("request", "", MediaType.APPLICATION_JSON_VALUE,
                                objectMapper.writeValueAsBytes(request)))
                        .cookie(admin).with(csrf()))
                .andReturn();
        var contractId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

        var terminateResult = mockMvc.perform(
                        post("/api/payroll/contracts/" + contractId + "/terminate").cookie(admin).with(csrf()))
                .andReturn();
        assertThat(terminateResult.getResponse().getStatus()).isEqualTo(200);
        assertThat(contracts.findById(UUID.fromString(contractId)).orElseThrow().getStatus())
                .isEqualTo(PayrollConstants.ContractStatus.TERMINATED);

        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            var entityIds = audit.recentActions(AuditConstants.EntityTypes.CONTRACT,
                    AuditConstants.Actions.CONTRACT_TERMINATE, 20).stream().map(a -> a.entityId()).toList();
            assertThat(entityIds).contains(contractId);
        });
    }

    @Test
    void refusesAnAccountWithOnlyPersonalScopePermissions() throws Exception {
        var teacher = signIn("payroll-outsider@eduerp.local", AccessConstants.RoleCodes.TEACHER);
        var employeeId = newEmployeeAccount("employee-5@eduerp.local");
        var request = new CreateContractRequest(employeeId, PayrollConstants.ContractType.OFFICIAL,
                new BigDecimal("9000000"), null, null, null, LocalDate.of(2026, 1, 1), List.of());

        var result = mockMvc.perform(multipart("/api/payroll/contracts")
                        .file(new MockMultipartFile("request", "", MediaType.APPLICATION_JSON_VALUE,
                                objectMapper.writeValueAsBytes(request)))
                        .cookie(teacher).with(csrf()))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
    }
}
