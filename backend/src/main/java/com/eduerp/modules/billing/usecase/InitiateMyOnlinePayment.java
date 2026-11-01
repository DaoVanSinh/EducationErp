package com.eduerp.modules.billing.usecase;

import com.eduerp.modules.billing.BillingConstants;
import com.eduerp.modules.billing.InvoiceNotFoundException;
import com.eduerp.modules.billing.dto.InitiateOnlinePaymentResponse;
import com.eduerp.modules.billing.internal.OnlinePaymentStarter;
import com.eduerp.modules.billing.internal.repository.InvoiceRepository;
import com.eduerp.modules.students.StudentsManagement;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Học viên tự thanh toán online (VNPay) hoá đơn của CHÍNH mình từ trang "Học phí của tôi".
 *
 * <p>Hồ sơ học viên luôn suy ra từ {@code accountId} của principal qua facade students - giống
 * {@code ListMyInvoices}, không bao giờ nhận từ client. Hoá đơn không thuộc về người gọi trả đúng
 * {@link InvoiceNotFoundException} như hoá đơn không tồn tại: không để lộ mã hoá đơn của người khác
 * có tồn tại hay không.
 */
@Service
public class InitiateMyOnlinePayment {

    private final StudentsManagement students;
    private final InvoiceRepository invoices;
    private final OnlinePaymentStarter starter;

    InitiateMyOnlinePayment(StudentsManagement students, InvoiceRepository invoices, OnlinePaymentStarter starter) {
        this.students = students;
        this.invoices = invoices;
        this.starter = starter;
    }

    @Transactional
    public InitiateOnlinePaymentResponse execute(UUID accountId, UUID invoiceId,
            BillingConstants.PaymentMethod gateway) {
        var profile = students.findProfileByAccountId(accountId)
                .orElseThrow(() -> new InvoiceNotFoundException(invoiceId));
        var invoice = invoices.findById(invoiceId)
                .filter(found -> found.getStudentProfileId().equals(profile.studentProfileId()))
                .orElseThrow(() -> new InvoiceNotFoundException(invoiceId));
        return starter.start(invoice, gateway);
    }
}
