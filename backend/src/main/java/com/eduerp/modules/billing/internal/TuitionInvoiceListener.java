package com.eduerp.modules.billing.internal;

import com.eduerp.modules.courses.CoursesEvents;
import com.eduerp.modules.enrollment.EnrollmentEvents;
import com.eduerp.modules.enrollment.EnrollmentManagement;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Giữ hoá đơn học phí khớp với ghi danh. Chạy đồng bộ trong transaction của thao tác gốc: lập hoá đơn lỗi thì ghi danh
 * cũng huỷ, không bao giờ có ghi danh thiếu hoá đơn vì lỗi nửa chừng.
 */
@Component
class TuitionInvoiceListener {

    private final TuitionInvoiceIssuer issuer;
    private final EnrollmentManagement enrollments;

    TuitionInvoiceListener(TuitionInvoiceIssuer issuer, EnrollmentManagement enrollments) {
        this.issuer = issuer;
        this.enrollments = enrollments;
    }

    @EventListener
    void on(EnrollmentEvents.EnrollmentCreated event) {
        enrollments.getEnrollment(event.enrollmentId()).ifPresent(
                enrollment -> issuer.issueFor(enrollment, event.actorAccountId(), event.actorBranchId()));
    }

    @EventListener
    void on(EnrollmentEvents.EnrollmentWithdrawn event) {
        issuer.settleWithdrawal(event.enrollmentId(), event.actorAccountId(), event.actorBranchId());
    }

    @EventListener
    void on(CoursesEvents.ClassStartDateChanged event) {
        issuer.rescheduleForClass(event.classId(), event.oldStartDate(), event.newStartDate());
    }
}
