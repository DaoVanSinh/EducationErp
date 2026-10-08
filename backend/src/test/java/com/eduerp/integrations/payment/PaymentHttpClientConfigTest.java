package com.eduerp.integrations.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * Final review Important #4: {@code MomoPaymentGatewayClient} gọi {@code RestClient} bên trong một
 * usecase {@code @Transactional} (InitiateOnlinePayment). Không có timeout nào từng được cấu hình -
 * một kết nối treo tới MoMo giữ một connection HikariCP vô thời hạn, một click có thể rút cạn pool
 * của toàn app. Test này chốt lại CÓ một giới hạn thời gian thật, không phải "mặc định vô hạn".
 */
class PaymentHttpClientConfigTest {

    @Test
    void connectAndReadTimeoutsAreBoundedNotInfinite() {
        assertThat(PaymentHttpClientConfig.CONNECT_TIMEOUT).isEqualTo(Duration.ofSeconds(5));
        assertThat(PaymentHttpClientConfig.READ_TIMEOUT).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    void theCustomizerAppliesACleanlyBuildableRequestFactoryToAnyBuilder() {
        var customizer = new PaymentHttpClientConfig().paymentGatewayTimeoutCustomizer();
        var builder = RestClient.builder();

        assertThatCode(() -> customizer.customize(builder)).doesNotThrowAnyException();
        assertThatCode(builder::build).doesNotThrowAnyException();
    }
}
