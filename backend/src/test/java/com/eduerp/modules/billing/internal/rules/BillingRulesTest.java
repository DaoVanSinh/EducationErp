package com.eduerp.modules.billing.internal.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.eduerp.modules.billing.BillingConstants;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Pure, 100% unit-test được bằng new, không I/O - mirror {@code PayrollRulesTest}. */
class BillingRulesTest {

    @Test
    void moneyRoundsToWholeDongBecauseVndHasNoSubUnitInThisSystem() {
        assertThat(BillingRules.money(new BigDecimal("1000000.49"))).isEqualByComparingTo(new BigDecimal("1000000"));
        assertThat(BillingRules.money(new BigDecimal("1000000.50"))).isEqualByComparingTo(new BigDecimal("1000001"));
        assertThat(BillingRules.money(new BigDecimal("1000000")).scale()).isZero();
    }

    @Test
    void remainingIsAmountMinusAmountPaid() {
        assertThat(BillingRules.remaining(new BigDecimal("6000000"), new BigDecimal("2000000")))
                .isEqualByComparingTo(new BigDecimal("4000000"));
    }

    @Test
    void statusIsPartiallyPaidWhileSomethingIsStillOwed() {
        assertThat(BillingRules.statusAfterPayment(new BigDecimal("6000000"), new BigDecimal("2000000")))
                .isEqualTo(BillingConstants.InvoiceStatus.PARTIALLY_PAID);
    }

    @Test
    void statusIsPaidWhenPaidExactlyInFull() {
        assertThat(BillingRules.statusAfterPayment(new BigDecimal("6000000"), new BigDecimal("6000000")))
                .isEqualTo(BillingConstants.InvoiceStatus.PAID);
    }

    /** Thanh toán vượt (cổng trả về số lớn hơn) vẫn là PAID, không quay lại PARTIALLY_PAID. */
    @Test
    void statusIsPaidWhenOverpaid() {
        assertThat(BillingRules.statusAfterPayment(new BigDecimal("6000000"), new BigDecimal("6000001")))
                .isEqualTo(BillingConstants.InvoiceStatus.PAID);
    }

    @Test
    void onlyUnpaidAndPartiallyPaidInvoicesArePayable() {
        assertThat(BillingRules.isPayable(BillingConstants.InvoiceStatus.UNPAID)).isTrue();
        assertThat(BillingRules.isPayable(BillingConstants.InvoiceStatus.PARTIALLY_PAID)).isTrue();
        assertThat(BillingRules.isPayable(BillingConstants.InvoiceStatus.PAID)).isFalse();
        assertThat(BillingRules.isPayable(BillingConstants.InvoiceStatus.CANCELLED)).isFalse();
    }

    /** Hoá đơn quá hạn vẫn phải thu được - OVERDUE chỉ là nhãn nhắc nợ, không phải khoá sổ. */
    @Test
    void overdueInvoicesRemainPayable() {
        assertThat(BillingRules.isPayable(BillingConstants.InvoiceStatus.OVERDUE)).isTrue();
    }

    /** VNPay yêu cầu mã giao dịch không trùng trong ngày - millis làm cho mỗi lần bấm là một mã mới,
     * kể cả khi người dùng bấm "Thu online" hai lần trên cùng một hoá đơn. */
    @Test
    void gatewayOrderIdCombinesInvoiceInstallmentAndTimestamp() {
        var invoiceId = UUID.fromString("0f8fad5b-d9cb-469f-a165-70867728950e");

        assertThat(BillingRules.gatewayOrderId(invoiceId, 2, 1767222123000L))
                .isEqualTo("0f8fad5b-d9cb-469f-a165-70867728950e-2-1767222123000");
    }

    @Test
    void gatewayOrderIdDiffersBetweenTwoAttemptsOnTheSameInvoice() {
        var invoiceId = UUID.randomUUID();

        assertThat(BillingRules.gatewayOrderId(invoiceId, 1, 1L))
                .isNotEqualTo(BillingRules.gatewayOrderId(invoiceId, 1, 2L));
    }

    /** Cột discount_percent là NUMERIC(5,2) - số trả về client phải khớp số sẽ lưu xuống. */
    @Test
    void percentRoundsToTwoDecimalsHalfUp() {
        assertThat(BillingRules.percent(new BigDecimal("15"))).isEqualByComparingTo(new BigDecimal("15.00"));
        assertThat(BillingRules.percent(new BigDecimal("12.345"))).isEqualByComparingTo(new BigDecimal("12.35"));
        assertThat(BillingRules.percent(new BigDecimal("15.00")).scale()).isEqualTo(2);
    }

    /** Tổng combo sau giảm là một cột tiền NUMERIC(14,0) - không để lại phần lẻ nào. */
    @Test
    void discountedTotalAppliesThePercentThenRoundsToWholeDong() {
        assertThat(BillingRules.discountedTotal(new BigDecimal("21000000"), new BigDecimal("15.00")))
                .isEqualByComparingTo(new BigDecimal("17850000"));
        assertThat(BillingRules.discountedTotal(new BigDecimal("21000000"), BigDecimal.ZERO))
                .isEqualByComparingTo(new BigDecimal("21000000"));
        assertThat(BillingRules.discountedTotal(new BigDecimal("1000001"), new BigDecimal("12.50")).scale())
                .isZero();
    }
}
