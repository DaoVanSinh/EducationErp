package com.eduerp.modules.billing.internal.rules;

import com.eduerp.modules.billing.BillingConstants;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;
import java.util.UUID;

/** Pure, 100% unit-test được bằng new, không I/O - mirror {@code PayrollRules}. */
public final class BillingRules {

    /** Mọi cột tiền của billing là NUMERIC(14,0) - số trả về client phải khớp số sẽ lưu xuống, nếu
     * không thì con số người dùng thấy lúc nhập lệch con số đối soát sau khi tải lại. */
    private static final int MONEY_SCALE = 0;

    /** Cột combo_discount_tiers.discount_percent / combos.discount_percent là NUMERIC(5,2). */
    private static final int PERCENT_SCALE = 2;

    /** Chia 100 trước khi nhân nên cần dư chữ số thập phân, nếu không 15% thành 0 (BigDecimal chia
     * theo scale của số bị chia). Kết quả cuối vẫn được money() kéo về scale 0. */
    private static final int DISCOUNT_MATH_SCALE = 6;

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    /** OVERDUE vẫn thu được: đó là nhãn nhắc nợ, không phải khoá sổ (chỉ PAID/CANCELLED mới chặn). */
    private static final Set<BillingConstants.InvoiceStatus> PAYABLE_STATUSES = Set.of(
            BillingConstants.InvoiceStatus.UNPAID, BillingConstants.InvoiceStatus.PARTIALLY_PAID,
            BillingConstants.InvoiceStatus.OVERDUE);

    private BillingRules() {
    }

    public static BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    public static BigDecimal percent(BigDecimal value) {
        return value.setScale(PERCENT_SCALE, RoundingMode.HALF_UP);
    }

    /** Tổng tiền combo sau giảm giá (spec mục 4): {@code total * (1 - percent/100)}, scale 0. */
    public static BigDecimal discountedTotal(BigDecimal totalOriginalAmount, BigDecimal discountPercent) {
        var multiplier = BigDecimal.ONE.subtract(
                percent(discountPercent).divide(ONE_HUNDRED, DISCOUNT_MATH_SCALE, RoundingMode.HALF_UP));
        return money(totalOriginalAmount.multiply(multiplier));
    }

    public static BigDecimal remaining(BigDecimal amount, BigDecimal amountPaid) {
        return money(amount.subtract(amountPaid));
    }

    public static BillingConstants.InvoiceStatus statusAfterPayment(BigDecimal amount, BigDecimal amountPaid) {
        return amountPaid.compareTo(amount) >= 0
                ? BillingConstants.InvoiceStatus.PAID : BillingConstants.InvoiceStatus.PARTIALLY_PAID;
    }

    public static boolean isPayable(BillingConstants.InvoiceStatus status) {
        return PAYABLE_STATUSES.contains(status);
    }

    /** VNPay đòi mã giao dịch không trùng trong ngày (spec mục 5) - millis đảm bảo điều đó ngay cả
     * khi cùng một hoá đơn được bấm thu online nhiều lần. */
    public static String gatewayOrderId(UUID invoiceId, int installmentNumber, long epochMilli) {
        return invoiceId + "-" + installmentNumber + "-" + epochMilli;
    }
}
