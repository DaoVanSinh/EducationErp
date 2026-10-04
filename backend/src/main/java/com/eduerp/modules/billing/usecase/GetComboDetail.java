package com.eduerp.modules.billing.usecase;

import com.eduerp.modules.billing.ComboNotFoundException;
import com.eduerp.modules.billing.dto.ComboDetailResponse;
import com.eduerp.modules.billing.internal.repository.ComboRepository;
import com.eduerp.modules.billing.internal.repository.InvoiceRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Mirror {@code GetInvoiceDetail}: tổng thể + các danh sách con, một query cho mỗi phần, không N+1. */
@Service
public class GetComboDetail {

    private final ComboRepository combos;
    private final InvoiceRepository invoices;

    GetComboDetail(ComboRepository combos, InvoiceRepository invoices) {
        this.combos = combos;
        this.invoices = invoices;
    }

    @Transactional(readOnly = true)
    public ComboDetailResponse execute(UUID comboId) {
        var combo = combos.findById(comboId).orElseThrow(() -> new ComboNotFoundException(comboId));
        // Thứ tự các khoá do @OrderBy trên Combo.enrollments quyết định - không sort lại ở đây.
        var members = combo.getEnrollments().stream().map(CreateCombo::toEnrollmentResponse).toList();
        // findAllByComboId KHÔNG lọc trạng thái: kế toán cần thấy cả đợt đã huỷ để hiểu vì sao số đợt
        // nhảy số (spec mục 6).
        var comboInvoices = invoices.findAllByComboId(comboId).stream()
                .map(CreateInvoice::toResponse).toList();
        return new ComboDetailResponse(CreateCombo.toResponse(combo), members, comboInvoices);
    }
}
