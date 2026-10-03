package com.eduerp.modules.billing.usecase;

import com.eduerp.modules.billing.BillingConstants;
import com.eduerp.modules.billing.BillingEvents;
import com.eduerp.modules.billing.CourseNotFoundException;
import com.eduerp.modules.billing.CourseTuitionNotConfiguredException;
import com.eduerp.modules.billing.EnrollmentNotActiveForBillingException;
import com.eduerp.modules.billing.EnrollmentNotFoundException;
import com.eduerp.modules.billing.InstallmentLimitExceededException;
import com.eduerp.modules.billing.InvoiceAmountExceedsTuitionException;
import com.eduerp.modules.billing.dto.CreateInvoiceRequest;
import com.eduerp.modules.billing.dto.InvoiceResponse;
import com.eduerp.modules.billing.internal.model.Invoice;
import com.eduerp.modules.billing.internal.repository.InvoiceRepository;
import com.eduerp.modules.courses.CoursesManagement;
import com.eduerp.modules.enrollment.EnrollmentConstants;
import com.eduerp.modules.enrollment.EnrollmentManagement;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Thứ tự kiểm tra đúng theo spec mục 5: ghi danh → học phí khoá → hạn mức 3 đợt → tổng tiền → lưu.
 * Hai lỗi "hết đợt" và "vượt học phí" là hai lỗi KHÁC NHAU (Review Focus #3) - kế toán cần biết
 * mình đang chạm giới hạn nào.
 */
@Service
public class CreateInvoice {

    private final InvoiceRepository invoices;
    private final EnrollmentManagement enrollmentManagement;
    private final CoursesManagement coursesManagement;
    private final ApplicationEventPublisher events;

    CreateInvoice(InvoiceRepository invoices, EnrollmentManagement enrollmentManagement,
            CoursesManagement coursesManagement, ApplicationEventPublisher events) {
        this.invoices = invoices;
        this.enrollmentManagement = enrollmentManagement;
        this.coursesManagement = coursesManagement;
        this.events = events;
    }

    @Transactional
    public InvoiceResponse execute(UUID actorAccountId, UUID actorBranchId, CreateInvoiceRequest request) {
        var enrollment = enrollmentManagement.getEnrollment(request.enrollmentId())
                .orElseThrow(() -> new EnrollmentNotFoundException(request.enrollmentId()));
        if (enrollment.status() != EnrollmentConstants.EnrollmentStatus.ACTIVE) {
            throw new EnrollmentNotActiveForBillingException(request.enrollmentId());
        }

        var tuition = coursesManagement.getCourseTuition(enrollment.courseId())
                .orElseThrow(() -> new CourseNotFoundException(enrollment.courseId()));
        if (tuition.tuitionFee() == null) {
            throw new CourseTuitionNotConfiguredException(enrollment.courseId());
        }

        var liveInvoiceCount = invoices.countByEnrollmentIdAndStatusNot(request.enrollmentId(),
                BillingConstants.InvoiceStatus.CANCELLED);
        if (liveInvoiceCount >= BillingConstants.Limits.MAX_INSTALLMENTS_PER_ENROLLMENT) {
            throw new InstallmentLimitExceededException(request.enrollmentId(),
                    BillingConstants.Limits.MAX_INSTALLMENTS_PER_ENROLLMENT);
        }

        var alreadyInvoiced = invoices
                .findAllByEnrollmentIdAndStatusNot(request.enrollmentId(), BillingConstants.InvoiceStatus.CANCELLED)
                .stream().map(Invoice::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        var totalAfterThisInvoice = alreadyInvoiced.add(request.amount());
        if (totalAfterThisInvoice.compareTo(tuition.tuitionFee()) > 0) {
            throw new InvoiceAmountExceedsTuitionException(totalAfterThisInvoice, tuition.tuitionFee());
        }

        var installmentNumber = Math.toIntExact(liveInvoiceCount) + 1;
        var saved = saveInvoice(new Invoice(request.enrollmentId(), enrollment.studentProfileId(),
                enrollment.courseId(), enrollment.branchId(), installmentNumber, request.amount(),
                request.dueDate(), actorAccountId), request.enrollmentId());
        events.publishEvent(new BillingEvents.InvoiceCreated(saved.getId(), actorAccountId, actorBranchId));
        return toResponse(saved);
    }

    /**
     * Final review Important #3: hai request đồng thời có thể cùng đọc count=1 và cùng tính
     * installmentNumber=2, vượt qua mọi kiểm tra ở trên vì cả hai đọc TRƯỚC khi ai kịp ghi. Migration
     * V20 (partial unique index trên {@code (enrollment_id, installment_number)}) là lớp chặn cuối -
     * dùng {@code saveAndFlush} để buộc INSERT chạy ngay ở đây, trong khối try/catch này, thay vì trôi
     * tới lúc transaction commit (ngoài tầm với của catch) rồi rơi thành 500.
     */
    private Invoice saveInvoice(Invoice invoice, UUID enrollmentId) {
        try {
            return invoices.saveAndFlush(invoice);
        } catch (DataIntegrityViolationException raceLostToAnotherRequest) {
            throw new InstallmentLimitExceededException(enrollmentId,
                    BillingConstants.Limits.MAX_INSTALLMENTS_PER_ENROLLMENT);
        }
    }

    /** Dùng lại ở mọi usecase billing khác - một chỗ map duy nhất (mirror CreateEnrollment.toResponse). */
    static InvoiceResponse toResponse(Invoice invoice) {
        return new InvoiceResponse(invoice.getId(), invoice.getEnrollmentId(), invoice.getStudentProfileId(),
                invoice.getCourseId(), invoice.getBranchId(), invoice.getInstallmentNumber(), invoice.getAmount(),
                invoice.getAmountPaid(), invoice.getStatus(), invoice.getDueDate(), invoice.getIssuedAt());
    }
}
