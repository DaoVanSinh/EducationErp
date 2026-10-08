package com.eduerp.integrations.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * Final review Important #4 của Phân hệ 3: {@code MomoPaymentGatewayClient} gọi {@code RestClient}
 * bên trong một usecase {@code @Transactional} ({@code InitiateOnlinePayment}). Không có timeout thì
 * một kết nối treo tới MoMo giữ một connection HikariCP vô thời hạn. Test này chốt lại CÓ một giới
 * hạn thời gian thật.
 *
 * <p>Phân hệ 5 đổi CÁCH áp giới hạn đó, không đổi giá trị: trước đây là một
 * {@code RestClientCustomizer} toàn cục, mà Spring AI cũng nhận chính
 * {@code ObjectProvider<RestClient.Builder>} của Boot - nên 10 giây của MoMo sẽ cắt ngang mọi lệnh
 * gọi Anthropic. Giờ request factory được áp tại đúng chỗ dùng nó.
 */
class PaymentHttpClientConfigTest {

    @Test
    void connectAndReadTimeoutsAreBoundedNotInfinite() {
        assertThat(PaymentHttpClientConfig.CONNECT_TIMEOUT).isEqualTo(Duration.ofSeconds(5));
        assertThat(PaymentHttpClientConfig.READ_TIMEOUT).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    void theRequestFactoryIsCleanlyBuildableAndAppliesToAnyBuilder() {
        var builder = RestClient.builder();

        assertThatCode(() -> builder.requestFactory(PaymentHttpClientConfig.timeoutRequestFactory()))
                .doesNotThrowAnyException();
        assertThatCode(builder::build).doesNotThrowAnyException();
    }

    /** Một builder "trần" (đúng thứ Spring AI sẽ nhận khi không còn customizer toàn cục) vẫn dựng
     * được MoMo client, và chính client đó tự áp timeout của mình. */
    @Test
    void theMomoClientAppliesTheTimeoutItselfFromAPlainBuilder() {
        var properties = new MomoProperties("PARTNER", "access", "secret",
                "https://example.test/create", "https://example.test/return",
                "https://example.test/ipn");

        assertThatCode(() -> new MomoPaymentGatewayClient(properties, RestClient.builder()))
                .doesNotThrowAnyException();
    }
}
