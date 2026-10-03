package com.eduerp.modules.billing;

import static org.assertj.core.api.Assertions.assertThat;

import com.eduerp.integrations.payment.PaymentGatewayType;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class BillingConstantsTest {

    @Test
    void invoiceStatusCoversTheWholeLifecycle() {
        assertThat(BillingConstants.InvoiceStatus.values()).containsExactly(
                BillingConstants.InvoiceStatus.UNPAID, BillingConstants.InvoiceStatus.PARTIALLY_PAID,
                BillingConstants.InvoiceStatus.PAID, BillingConstants.InvoiceStatus.OVERDUE,
                BillingConstants.InvoiceStatus.CANCELLED);
    }

    @Test
    void installmentLimitIsThree() {
        assertThat(BillingConstants.Limits.MAX_INSTALLMENTS_PER_ENROLLMENT).isEqualTo(3);
    }

    /**
     * Mỗi {@code PaymentMethod} online phải trùng tên với một {@code PaymentGatewayType} -
     * {@code PaymentGatewayClientResolver} (Task 14) map hai enum bằng {@code name()}, lệch tên là
     * lỗi runtime chứ không phải lỗi biên dịch.
     */
    @Test
    void everyOnlinePaymentMethodNamesAnExistingGatewayType() {
        var gatewayNames = Arrays.stream(PaymentGatewayType.values()).map(Enum::name).toList();

        assertThat(gatewayNames).contains(BillingConstants.PaymentMethod.MOMO.name(),
                BillingConstants.PaymentMethod.VNPAY.name());
        assertThat(gatewayNames).doesNotContain(BillingConstants.PaymentMethod.MANUAL.name());
    }

    @Test
    void markOverdueCronRunsOnceAtOneInTheMorning() {
        assertThat(BillingConstants.Schedules.MARK_OVERDUE_CRON).isEqualTo("0 0 1 * * *");
    }
}
