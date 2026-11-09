package com.eduerp.modules.billing.internal;

import com.eduerp.modules.billing.BillingConstants;
import com.eduerp.modules.billing.StudentHasUnpaidInvoicesException;
import com.eduerp.modules.billing.internal.repository.InvoiceRepository;
import com.eduerp.modules.identity.AccountLifecycleParticipant;
import com.eduerp.modules.students.StudentsManagement;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Còn nợ học phí thì chưa đóng tài khoản được: phải thu xong hoặc huỷ hoá đơn trước. */
@Component
class StudentTuitionLifecycle implements AccountLifecycleParticipant {

    private static final List<BillingConstants.InvoiceStatus> UNPAID = List.of(BillingConstants.InvoiceStatus.UNPAID,
            BillingConstants.InvoiceStatus.PARTIALLY_PAID, BillingConstants.InvoiceStatus.OVERDUE);

    private final InvoiceRepository invoices;
    private final StudentsManagement students;

    StudentTuitionLifecycle(InvoiceRepository invoices, StudentsManagement students) {
        this.invoices = invoices;
        this.students = students;
    }

    @Override
    public void beforeClose(UUID accountId) {
        var hasDebt = students.findProfileByAccountId(accountId)
                .map(profile -> invoices.existsByStudentProfileIdAndStatusIn(profile.studentProfileId(), UNPAID))
                .orElse(false);
        if (hasDebt) {
            throw new StudentHasUnpaidInvoicesException(accountId);
        }
    }
}
