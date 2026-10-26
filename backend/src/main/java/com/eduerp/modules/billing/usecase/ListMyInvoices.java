package com.eduerp.modules.billing.usecase;

import com.eduerp.modules.billing.dto.InvoiceDetailResponse;
import com.eduerp.modules.billing.dto.PaymentResponse;
import com.eduerp.modules.billing.internal.model.Invoice;
import com.eduerp.modules.billing.internal.model.Payment;
import com.eduerp.modules.billing.internal.repository.InvoiceRepository;
import com.eduerp.modules.billing.internal.repository.PaymentRepository;
import com.eduerp.modules.students.StudentsManagement;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cổng học phí của học viên: mọi hoá đơn của CHÍNH người gọi kèm lịch sử thanh toán (đề cương ĐATN -
 * "theo dõi tình trạng học phí và lịch sử thanh toán", học viên tự tra cứu thay vì hỏi trung tâm).
 * Chỉ ĐỌC - thu tiền vẫn là thao tác của admin ({@code UPDATE_INVOICE}).
 *
 * <p>{@code studentProfileId} lấy từ {@code accountId} của principal qua facade students, không
 * bao giờ nhận từ client. Tài khoản chưa có hồ sơ học viên trả danh sách rỗng.
 */
@Service
public class ListMyInvoices {

    private final StudentsManagement students;
    private final InvoiceRepository invoices;
    private final PaymentRepository payments;

    ListMyInvoices(StudentsManagement students, InvoiceRepository invoices, PaymentRepository payments) {
        this.students = students;
        this.invoices = invoices;
        this.payments = payments;
    }

    @Transactional(readOnly = true)
    public List<InvoiceDetailResponse> execute(UUID accountId) {
        var profile = students.findProfileByAccountId(accountId).orElse(null);
        if (profile == null) {
            return List.of();
        }
        var mine = invoices.search(profile.studentProfileId(), null, null, Pageable.unpaged()).getContent();
        if (mine.isEmpty()) {
            return List.of();
        }

        var invoiceIds = mine.stream().map(Invoice::getId).toList();
        // Một câu cho cả lịch sử; thứ tự createdAt DESC do query quyết định và groupingBy giữ nguyên thứ tự đó.
        Map<UUID, List<PaymentResponse>> history = payments.findAllByInvoice_IdInOrderByCreatedAtDesc(invoiceIds)
                .stream()
                .collect(Collectors.groupingBy(payment -> payment.getInvoice().getId(),
                        Collectors.mapping(ListMyInvoices::toPaymentResponse, Collectors.toList())));

        return mine.stream()
                .sorted(Comparator.comparing(Invoice::getIssuedAt).reversed())
                .map(invoice -> new InvoiceDetailResponse(CreateInvoice.toResponse(invoice),
                        history.getOrDefault(invoice.getId(), List.of())))
                .toList();
    }

    private static PaymentResponse toPaymentResponse(Payment payment) {
        return GetInvoiceDetail.toPaymentResponse(payment);
    }
}
