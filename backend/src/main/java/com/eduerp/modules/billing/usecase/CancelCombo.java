package com.eduerp.modules.billing.usecase;

import com.eduerp.modules.billing.BillingEvents;
import com.eduerp.modules.billing.ComboHasInvoicesException;
import com.eduerp.modules.billing.ComboNotFoundException;
import com.eduerp.modules.billing.internal.repository.ComboRepository;
import com.eduerp.modules.billing.internal.repository.InvoiceRepository;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Huỷ một combo gộp nhầm. CHỈ cho phép khi chưa phát hành hoá đơn nào - {@code countByComboId} đếm
 * MỌI trạng thái, kể cả {@code CANCELLED} (Review Focus #6): một combo từng có chứng từ tài chính
 * gắn vào thì không còn là bản nháp, cùng nguyên tắc mà {@code CancelInvoice} áp dụng cho hoá đơn.
 *
 * <p>Huỷ là XOÁ CỨNG {@code Combo} (cascade xoá {@code ComboEnrollment}), không phải chuyển trạng
 * thái: {@code Combo} cố ý không có cột {@code status}, và việc xoá cứng chính là thứ cho phép
 * UNIQUE {@code combo_enrollments.enrollment_id} là unique đầy đủ thay vì partial (spec mục 4).
 *
 * <p>Dùng quyền {@code UPDATE_INVOICE}, KHÔNG dùng {@code Actions.DELETE} - toàn hệ thống chưa dùng
 * DELETE ở bất kỳ resource nào.
 */
@Service
public class CancelCombo {

    private final ComboRepository combos;
    private final InvoiceRepository invoices;
    private final ApplicationEventPublisher events;

    CancelCombo(ComboRepository combos, InvoiceRepository invoices, ApplicationEventPublisher events) {
        this.combos = combos;
        this.invoices = invoices;
        this.events = events;
    }

    @Transactional
    public void execute(UUID comboId, UUID actorAccountId, UUID actorBranchId) {
        var combo = combos.findById(comboId).orElseThrow(() -> new ComboNotFoundException(comboId));
        if (invoices.countByComboId(comboId) > 0) {
            throw new ComboHasInvoicesException(comboId);
        }
        // Phát TRƯỚC khi xoá: sau lệnh delete không còn gì để đọc, mà đây là dấu vết duy nhất cho
        // thấy combo này từng tồn tại. Cùng transaction nên nếu xoá hỏng thì event cũng không gửi.
        events.publishEvent(new BillingEvents.ComboCancelled(comboId, actorAccountId, actorBranchId));
        combos.delete(combo);
    }
}
