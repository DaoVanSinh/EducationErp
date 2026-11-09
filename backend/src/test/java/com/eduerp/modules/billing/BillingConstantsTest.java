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

    /** Ngày khai giảng là ngày thứ nhất của 7 ngày đóng học phí. */
    @Test
    void tuitionIsDueOnTheSeventhDayFromTheClassStart() {
        assertThat(BillingConstants.Limits.TUITION_DUE_DAYS_AFTER_START).isEqualTo(6);
    }

    /**
     * Mỗi {@code PaymentMethod} online phải trùng tên với một {@code PaymentGatewayType} -
     * {@code PaymentGatewayClientResolver} (Task 14) map hai enum bằng {@code name()}, lệch tên là
     * lỗi runtime chứ không phải lỗi biên dịch.
     */
    @Test
    void everyOnlinePaymentMethodNamesAnExistingGatewayType() {
        var gatewayNames = Arrays.stream(PaymentGatewayType.values()).map(Enum::name).toList();

        assertThat(gatewayNames).contains(BillingConstants.PaymentMethod.VNPAY.name());
        // MoMo đã gỡ cổng; thu tay không bao giờ có cổng - resolver phải từ chối cả hai.
        assertThat(gatewayNames).doesNotContain(BillingConstants.PaymentMethod.MOMO.name(),
                BillingConstants.PaymentMethod.MANUAL.name(), BillingConstants.PaymentMethod.CASH.name(),
                BillingConstants.PaymentMethod.BANK_TRANSFER.name());
    }

    @Test
    void markOverdueCronRunsOnceAtOneInTheMorning() {
        assertThat(BillingConstants.Schedules.MARK_OVERDUE_CRON).isEqualTo("0 0 1 * * *");
    }

    @Test
    void aComboNeedsTwoEnrollments() {
        assertThat(BillingConstants.Limits.MIN_ENROLLMENTS_PER_COMBO).isEqualTo(2);
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
