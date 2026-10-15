package com.eduerp.core.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.AccessManagement;
import com.eduerp.modules.identity.internal.model.Account;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redis.testcontainers.RedisContainer;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Bắt buộc chạy qua Tomcat thật (RANDOM_PORT), KHÔNG dùng MockMvc: lỗi gốc nằm ở lượt dispatch
 * {@code /error} của servlet container - MockMvc không giả lập lượt đó, nên mọi IT cũ vẫn thấy
 * 403/400 trong khi server thật trả 401 cho cùng request, và frontend đăng xuất người dùng.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ErrorResponsesOverRealHttpIT {

    private static final String PASSWORD = "Password123!";
    private static final String CSRF_COOKIE = "XSRF-TOKEN";
    private static final String CSRF_HEADER = "X-XSRF-TOKEN";
    private static final String JSON = "application/json";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Container
    @ServiceConnection
    static RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7"));

    @LocalServerPort
    int port;

    @Autowired
    AccountRepository accounts;

    @Autowired
    AccessManagement access;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    ObjectMapper objectMapper;

    /** Tự giữ cookie: cookie phiên mang cờ Secure (mặc định), CookieManager của JDK sẽ không gửi
     * lại qua http://localhost nên mọi request sau đăng nhập đều thành ẩn danh. */
    private final Map<String, String> cookies = new LinkedHashMap<>();
    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void signInAsStudent() throws Exception {
        var email = "err-hv-" + UUID.randomUUID().toString().substring(0, 8) + "@eduerp.local";
        var account = new Account(email, passwordEncoder.encode(PASSWORD), "Học viên lỗi", null);
        account.recordFirstLogin();
        var accountId = accounts.save(account).getId();
        access.assignRole(accountId, AccessConstants.RoleCodes.STUDENT);
        access.evictPermissionCache(accountId);

        var login = send(HttpRequest.newBuilder(uri("/api/auth/login"))
                .header("Content-Type", JSON)
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}")));
        assertThat(login.statusCode()).isEqualTo(200);
    }

    @Test
    void missingPermissionIsForbiddenNotUnauthorized() throws Exception {
        var response = send(HttpRequest.newBuilder(uri("/api/dashboard/stats")).GET());

        assertProblem(response, 403, ProblemDetails.ErrorCodes.ACCESS_DENIED);
    }

    @Test
    void malformedJsonIsBadRequest() throws Exception {
        send(HttpRequest.newBuilder(uri("/api/account/me")).GET());
        var response = send(HttpRequest.newBuilder(uri("/api/account/change-password"))
                .header("Content-Type", JSON)
                .header(CSRF_HEADER, csrfToken())
                .POST(HttpRequest.BodyPublishers.ofString("{hỏng")));

        assertProblem(response, 400, "BAD_REQUEST");
    }

    @Test
    void malformedUuidIsBadRequest() throws Exception {
        var response = send(HttpRequest.newBuilder(uri("/api/billing/invoices/khong-phai-uuid")).GET());

        assertProblem(response, 400, "BAD_REQUEST");
    }

    @Test
    void wrongMethodIsMethodNotAllowed() throws Exception {
        var response = send(HttpRequest.newBuilder(uri("/api/account/change-password")).GET());

        assertProblem(response, 405, "METHOD_NOT_ALLOWED");
    }

    @Test
    void unknownRouteIsNotFound() throws Exception {
        var response = send(HttpRequest.newBuilder(uri("/api/khong-ton-tai")).GET());

        assertProblem(response, 404, "NOT_FOUND");
    }

    /** CSRF bị chặn ở filter, trước controller: phải ra 403 ProblemDetail chứ không phải 401. */
    @Test
    void missingCsrfTokenIsForbiddenNotUnauthorized() throws Exception {
        var response = send(HttpRequest.newBuilder(uri("/api/auth/logout"))
                .POST(HttpRequest.BodyPublishers.noBody()));

        assertProblem(response, 403, ProblemDetails.ErrorCodes.ACCESS_DENIED);
    }

    @Test
    void anonymousRequestStaysUnauthorizedWithProblemDetail() throws Exception {
        var anonymous = HttpClient.newHttpClient();
        var response = anonymous.send(HttpRequest.newBuilder(uri("/api/dashboard/stats")).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertProblem(response, 401, ProblemDetails.ErrorCodes.UNAUTHENTICATED);
    }

    private void assertProblem(HttpResponse<String> response, int status, String errorCode) throws Exception {
        assertThat(response.statusCode()).isEqualTo(status);
        JsonNode body = objectMapper.readTree(response.body());
        assertThat(body.path(ProblemDetails.Properties.ERROR_CODE).asText()).isEqualTo(errorCode);
    }

    private HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
        if (!cookies.isEmpty()) {
            request.header("Cookie", cookies.entrySet().stream()
                    .map(cookie -> cookie.getKey() + "=" + cookie.getValue())
                    .collect(Collectors.joining("; ")));
        }
        var response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        response.headers().allValues("Set-Cookie").stream()
                .flatMap(header -> HttpCookie.parse(header).stream())
                .forEach(cookie -> cookies.put(cookie.getName(), cookie.getValue()));
        return response;
    }

    private String csrfToken() {
        return cookies.get(CSRF_COOKIE);
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
