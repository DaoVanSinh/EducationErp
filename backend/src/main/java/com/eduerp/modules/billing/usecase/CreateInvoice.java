package com.eduerp.modules.billing.usecase;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.billing.CourseNotFoundException;
import com.eduerp.modules.billing.CourseTuitionNotConfiguredException;
import com.eduerp.modules.billing.EnrollmentAlreadyInvoicedException;
import com.eduerp.modules.billing.EnrollmentBilledByComboException;
import com.eduerp.modules.billing.EnrollmentNotActiveForBillingException;
import com.eduerp.modules.billing.EnrollmentNotFoundException;
import com.eduerp.modules.billing.dto.CreateInvoiceRequest;
import com.eduerp.modules.billing.dto.InvoiceResponse;
import com.eduerp.modules.billing.internal.TuitionInvoiceIssuer;
import com.eduerp.modules.billing.internal.access.BillingBranchGuard;
import com.eduerp.modules.billing.internal.pricing.EnrollmentDiscounts;
import com.eduerp.modules.billing.internal.response.InvoiceResponseAssembler;
import com.eduerp.modules.courses.CoursesManagement;
import com.eduerp.modules.enrollment.EnrollmentConstants;
import com.eduerp.modules.enrollment.EnrollmentManagement;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hoá đơn được lập tự động khi ghi danh, nên thao tác này chỉ dùng để lập lại: sau khi hoá đơn bị huỷ, khi khoá mới có
 * học phí, hoặc phần chưa lập của dữ liệu chia đợt cũ. Số tiền và hạn do hệ thống tính, người dùng không nhập.
 */
@Service
public class CreateInvoice {

    private final EnrollmentManagement enrollmentManagement;
    private final CoursesManagement coursesManagement;
    private final BillingBranchGuard branchGuard;
    private final EnrollmentDiscounts discounts;
    private final TuitionInvoiceIssuer issuer;
    private final InvoiceResponseAssembler assembler;

    CreateInvoice(EnrollmentManagement enrollmentManagement, CoursesManagement coursesManagement,
            BillingBranchGuard branchGuard, EnrollmentDiscounts discounts, TuitionInvoiceIssuer issuer,
            InvoiceResponseAssembler assembler) {
        this.enrollmentManagement = enrollmentManagement;
        this.coursesManagement = coursesManagement;
        this.branchGuard = branchGuard;
        this.discounts = discounts;
        this.issuer = issuer;
        this.assembler = assembler;
    }

    @Transactional
    public InvoiceResponse execute(UUID actorAccountId, UUID actorBranchId, CreateInvoiceRequest request) {
        var enrollmentId = request.enrollmentId();
        var enrollment = enrollmentManagement.getEnrollment(enrollmentId)
                .orElseThrow(() -> new EnrollmentNotFoundException(enrollmentId));
        branchGuard.require(actorAccountId, AccessConstants.Actions.CREATE, enrollment.branchId());
        if (enrollment.status() != EnrollmentConstants.EnrollmentStatus.ACTIVE) {
            throw new EnrollmentNotActiveForBillingException(enrollmentId);
        }
        if (discounts.isInCombo(enrollmentId)) {
            throw new EnrollmentBilledByComboException(enrollmentId);
        }
        requireTuitionConfigured(enrollmentId, enrollment.courseId());
        try {
            var saved = issuer.issueFor(enrollment, actorAccountId, actorBranchId)
                    .orElseThrow(() -> new EnrollmentAlreadyInvoicedException(enrollmentId));
            return assembler.toResponse(saved);
        } catch (DataIntegrityViolationException raceLostToAnotherRequest) {
            // uq_invoices_enrollment_installment là lớp chặn cuối khi hai request cùng lập một lúc.
            throw new EnrollmentAlreadyInvoicedException(enrollmentId);
        }
    }

    /** Lập lại thủ công thì báo rõ lý do, khác lập tự động: khoá chưa có giá thì ghi danh vẫn thành công. */
    private void requireTuitionConfigured(UUID enrollmentId, UUID courseId) {
        if (discounts.groupTuitionOf(enrollmentId).isPresent()) {
            return;
        }
        var tuition = coursesManagement.getCourseTuition(courseId)
                .orElseThrow(() -> new CourseNotFoundException(courseId));
        if (tuition.tuitionFee() == null) {
            throw new CourseTuitionNotConfiguredException(courseId);
        }
    }
}
