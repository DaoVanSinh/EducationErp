package com.eduerp.modules.billing;

import static org.assertj.core.api.Assertions.assertThat;

import com.eduerp.integrations.payment.PaymentGatewayType;
import java.util.Arrays;
import java.util.UUID;
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

    /** Spec mục 1 + 12: 3 đợt là cho CẢ combo, không phải 3 đợt mỗi khoá - hai hạn mức là hai hằng
     * riêng để không ai vô tình dùng chung rồi nhân lên theo số khoá. */
    @Test
    void comboLimitsAreThreeInstallmentsAndTwoEnrollments() {
        assertThat(BillingConstants.Limits.MAX_INSTALLMENTS_PER_COMBO).isEqualTo(3);
        assertThat(BillingConstants.Limits.MIN_ENROLLMENTS_PER_COMBO).isEqualTo(2);
        assertThat(BillingConstants.Limits.MAX_INSTALLMENTS_PER_ENROLLMENT).isEqualTo(3);
    }

    /** Hai event combo mang đúng hình dạng 4 event billing đã có: (id, actorAccountId, actorBranchId). */
    @Test
    void comboEventsCarryTheComboIdAndTheActor() {
        var comboId = UUID.randomUUID();
        var actorAccountId = UUID.randomUUID();
        var actorBranchId = UUID.randomUUID();

        var created = new BillingEvents.ComboCreated(comboId, actorAccountId, actorBranchId);
        assertThat(created.comboId()).isEqualTo(comboId);
        assertThat(created.actorAccountId()).isEqualTo(actorAccountId);
        assertThat(created.actorBranchId()).isEqualTo(actorBranchId);

        var cancelled = new BillingEvents.ComboCancelled(comboId, actorAccountId, actorBranchId);
        assertThat(cancelled.comboId()).isEqualTo(comboId);
        assertThat(cancelled.actorAccountId()).isEqualTo(actorAccountId);
    }
}
