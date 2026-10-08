package com.eduerp.modules.billing.usecase;

import com.eduerp.modules.billing.BillingConstants;
import com.eduerp.modules.billing.BillingEvents;
import com.eduerp.modules.billing.ComboNotFoundException;
import com.eduerp.modules.billing.InstallmentLimitExceededException;
import com.eduerp.modules.billing.InvoiceAmountExceedsTuitionException;
import com.eduerp.modules.billing.dto.CreateComboInvoiceRequest;
import com.eduerp.modules.billing.dto.InvoiceResponse;
import com.eduerp.modules.billing.internal.model.Invoice;
import com.eduerp.modules.billing.internal.repository.ComboRepository;
import com.eduerp.modules.billing.internal.repository.InvoiceRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mirror {@code CreateInvoice} nhưng neo theo {@code comboId}: tối đa 3 đợt cho CẢ combo (không
 * phải 3 đợt mỗi khoá), tổng không vượt {@code Combo.totalDiscountedAmount} - tức tổng SAU giảm giá,
 * không phải tổng gốc (spec mục 6).
 *
 * <p>Hai lỗi "hết đợt" và "vượt tổng tiền" là hai lỗi KHÁC NHAU (Review Focus #5) - kế toán cần biết
 * mình đang chạm giới hạn nào.
 *
 * <p>Hoá đơn tạo ra có {@code enrollmentId = null}, {@code courseId = null}; mọi luồng thu tiền của
 * Phase 3 ({@code InitiateOnlinePayment}/{@code RecordManualPayment}/{@code HandlePaymentCallback}/
 * {@code CancelInvoice}/{@code MarkOverdueInvoices}) chạy đúng trên nó mà không cần một nhánh rẽ
 * nào, vì chúng chỉ thao tác qua {@code id}/{@code amount}/{@code status} (Review Focus #9).
 */
@Service
public class CreateComboInvoice {

    private final InvoiceRepository invoices;
    private final ComboRepository combos;
    private final ApplicationEventPublisher events;

    CreateComboInvoice(InvoiceRepository invoices, ComboRepository combos,
            ApplicationEventPublisher events) {
        this.invoices = invoices;
        this.combos = combos;
        this.events = events;
    }

    @Transactional
    public InvoiceResponse execute(UUID actorAccountId, UUID actorBranchId,
            CreateComboInvoiceRequest request) {
        var combo = combos.findById(request.comboId())
                .orElseThrow(() -> new ComboNotFoundException(request.comboId()));

        var liveInvoices = invoices.findAllByComboIdAndStatusNot(request.comboId(),
                BillingConstants.InvoiceStatus.CANCELLED);
        if (liveInvoices.size() >= BillingConstants.Limits.MAX_INSTALLMENTS_PER_COMBO) {
            throw InstallmentLimitExceededException.forCombo(request.comboId(),
                    BillingConstants.Limits.MAX_INSTALLMENTS_PER_COMBO);
        }

        var alreadyInvoiced = liveInvoices.stream().map(Invoice::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        var totalAfterThisInvoice = alreadyInvoiced.add(request.amount());
        if (totalAfterThisInvoice.compareTo(combo.getTotalDiscountedAmount()) > 0) {
            throw InvoiceAmountExceedsTuitionException.forCombo(totalAfterThisInvoice,
                    combo.getTotalDiscountedAmount());
        }

        var installmentNumber = nextFreeInstallmentNumber(liveInvoices, combo.getId());
        var saved = saveInvoice(Invoice.forCombo(combo.getId(), combo.getStudentProfileId(),
                combo.getBranchId(), installmentNumber, request.amount(), request.dueDate(), actorAccountId),
                combo.getId());
        events.publishEvent(new BillingEvents.InvoiceCreated(saved.getId(), actorAccountId, actorBranchId));
        return CreateInvoice.toResponse(saved);
    }

    /**
     * Final review Important: một đợt đã huỷ GIẢI PHÓNG đúng số thứ tự của nó, nên số đợt mới phải
     * là số NHỎ NHẤT còn trống trong [1, MAX] - không phải "đếm số đợt sống rồi +1". Huỷ một đợt
     * KHÔNG PHẢI đợt cuối (ví dụ huỷ #1 khi #2 vẫn sống) thì đếm+1 sẽ tính ra đúng số #2 đang giữ,
     * INSERT va UNIQUE (combo_id, installment_number), và bị dịch nhầm thành "đã đủ 3 đợt" dù mới có
     * 1 đợt sống và còn thừa ngân sách.
     */
    private static int nextFreeInstallmentNumber(List<Invoice> liveInvoices, UUID comboId) {
        var usedNumbers = liveInvoices.stream().map(Invoice::getInstallmentNumber).collect(Collectors.toSet());
        for (int candidate = 1; candidate <= BillingConstants.Limits.MAX_INSTALLMENTS_PER_COMBO; candidate++) {
            if (!usedNumbers.contains(candidate)) {
                return candidate;
            }
        }
        // Không thể tới đây: liveInvoices.size() < MAX đã được kiểm ở trên, nên luôn có một số trống.
        throw InstallmentLimitExceededException.forCombo(comboId, BillingConstants.Limits.MAX_INSTALLMENTS_PER_COMBO);
    }

    /** Mirror {@code CreateInvoice.saveInvoice}: {@code uq_invoices_combo_installment} (V22) là lớp
     * chặn cuối cho hai request đồng thời cùng tính ra một số đợt; {@code saveAndFlush} để INSERT
     * chạy ngay trong khối try này thay vì trôi tới lúc commit rồi rơi thành 500. */
    private Invoice saveInvoice(Invoice invoice, UUID comboId) {
        try {
            return invoices.saveAndFlush(invoice);
        } catch (DataIntegrityViolationException raceLostToAnotherRequest) {
            throw InstallmentLimitExceededException.forCombo(comboId,
                    BillingConstants.Limits.MAX_INSTALLMENTS_PER_COMBO);
        }
    }
}
