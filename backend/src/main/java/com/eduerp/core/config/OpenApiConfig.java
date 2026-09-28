package com.eduerp.core.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Tài liệu API tự sinh từ chính các {@code @RestController}/DTO đã có — không phải viết tay và giữ
 * đồng bộ tay. Xem ở {@code /swagger-ui.html} (permitAll trong
 * {@code identity.web.IdentitySecurityConfig}), raw JSON ở {@code /v3/api-docs}.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI eduErpOpenApi() {
        return new OpenAPI().info(new Info()
                .title("EduERP API")
                .version("v1")
                .description("API quản lý đào tạo (QLDT) — Phân hệ Identity & Access."));
    }
}
