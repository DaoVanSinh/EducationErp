package com.eduerp.modules.billing.internal.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.eduerp.modules.billing.BillingConstants;
import com.eduerp.modules.billing.internal.model.Invoice;
import com.eduerp.modules.billing.internal.model.Payment;
import com.eduerp.modules.courses.internal.model.Class;
import com.eduerp.modules.courses.internal.model.Course;
import com.eduerp.modules.courses.internal.repository.ClassRepository;
import com.eduerp.modules.courses.internal.repository.CourseRepository;
import com.eduerp.modules.enrollment.internal.model.Enrollment;
import com.eduerp.modules.enrollment.internal.repository.EnrollmentRepository;
import com.eduerp.modules.identity.internal.model.Account;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import com.eduerp.modules.organization.internal.model.Branch;
import com.eduerp.modules.organization.internal.repository.BranchRepository;
import com.eduerp.modules.students.internal.model.StudentProfile;
import com.eduerp.modules.students.internal.repository.StudentProfileRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@DataJpaTest
class BillingRepositoryIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    InvoiceRepository invoices;

    @Autowired
    PaymentRepository payments;

    @Autowired
    EnrollmentRepository enrollments;

    @Autowired
    CourseRepository courses;

    @Autowired
    ClassRepository classes;

    @Autowired
    BranchRepository branches;

    @Autowired
    AccountRepository accounts;

    @Autowired
    StudentProfileRepository profiles;

    private UUID enrollmentId;
    private UUID studentProfileId;
    private UUID courseId;
    private UUID branchId;
    private UUID actorId;

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    @BeforeEach
    void seedEnrollment() {
        branchId = branches.save(new Branch("BIL-" + shortId(), "Chi nhánh billing", null)).getId();
        actorId = accounts.save(new Account("bil-actor-" + shortId() + "@eduerp.local", "hash", "Actor", null))
                .getId();
        var course = courses.save(new Course("BIL-C-" + shortId(), "Khoá billing", null, 24,
                new BigDecimal("12000000")));
        courseId = course.getId();
        var teacherId = accounts.save(new Account("bil-gv-" + shortId() + "@eduerp.local", "hash", "GV", null))
                .getId();
        var classId = classes.save(new Class(course, "BIL-K-" + shortId(), branchId, teacherId, 20)).getId();
        var studentAccountId = accounts
                .save(new Account("bil-hv-" + shortId() + "@eduerp.local", "hash", "HV", null)).getId();
        studentProfileId = profiles.save(new StudentProfile(studentAccountId, null, null, null)).getId();
        enrollmentId = enrollments
                .save(new Enrollment(studentProfileId, classId, courseId, branchId, actorId)).getId();
    }

    private Invoice newInvoice(int installmentNumber, String amount) {
        return new Invoice(enrollmentId, studentProfileId, courseId, branchId, installmentNumber,
                new BigDecimal(amount), LocalDate.of(2026, 11, 30), actorId);
    }

    @Test
    void savesAnInvoiceAsUnpaidWithZeroPaid() {
        var saved = invoices.save(newInvoice(1, "6000000"));

        var found = invoices.findById(saved.getId()).orElseThrow();
        assertThat(found.getStatus()).isEqualTo(BillingConstants.InvoiceStatus.UNPAID);
        assertThat(found.getAmountPaid()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(found.getInstallmentNumber()).isEqualTo(1);
        assertThat(found.getIssuedAt()).isNotNull();
    }

    @Test
    void countsAndSumsOnlyInvoicesThatAreNotCancelled() {
        invoices.saveAndFlush(newInvoice(1, "6000000"));
        var cancelled = invoices.saveAndFlush(newInvoice(2, "6000000"));
        cancelled.cancel();
        invoices.saveAndFlush(cancelled);

        assertThat(invoices.countByEnrollmentIdAndStatusNot(enrollmentId,
                BillingConstants.InvoiceStatus.CANCELLED)).isEqualTo(1);
        var live = invoices.findAllByEnrollmentIdAndStatusNot(enrollmentId,
                BillingConstants.InvoiceStatus.CANCELLED);
        assertThat(live).singleElement()
                .satisfies(invoice -> assertThat(invoice.getAmount()).isEqualByComparingTo(new BigDecimal("6000000")));
    }

    @Test
    void findsOverdueCandidatesByStatusAndDueDate() {
        var pastDue = invoices.saveAndFlush(new Invoice(enrollmentId, studentProfileId, courseId, branchId, 1,
                new BigDecimal("6000000"), LocalDate.of(2020, 1, 1), actorId));
        invoices.saveAndFlush(new Invoice(enrollmentId, studentProfileId, courseId, branchId, 2,
                new BigDecimal("6000000"), LocalDate.of(2099, 1, 1), actorId));

        var candidates = invoices.findAllByStatusInAndDueDateBefore(
                List.of(BillingConstants.InvoiceStatus.UNPAID, BillingConstants.InvoiceStatus.PARTIALLY_PAID),
                LocalDate.of(2026, 10, 3));

        assertThat(candidates).extracting(Invoice::getId).containsExactly(pastDue.getId());
    }

    @Test
    void searchFiltersByEveryCombinationOfStudentEnrollmentAndStatus() {
        var unpaid = invoices.saveAndFlush(newInvoice(1, "6000000"));
        var cancelled = invoices.saveAndFlush(newInvoice(2, "6000000"));
        cancelled.cancel();
        invoices.saveAndFlush(cancelled);
        var pageable = PageRequest.of(0, 20);

        assertThat(invoices.search(null, null, null, pageable).getTotalElements()).isEqualTo(2);
        assertThat(invoices.search(studentProfileId, null, null, pageable).getTotalElements()).isEqualTo(2);
        assertThat(invoices.search(null, enrollmentId, null, pageable).getTotalElements()).isEqualTo(2);
        assertThat(invoices.search(null, null, BillingConstants.InvoiceStatus.UNPAID, pageable).getContent())
                .extracting(Invoice::getId).containsExactly(unpaid.getId());
        assertThat(invoices.search(studentProfileId, enrollmentId, BillingConstants.InvoiceStatus.CANCELLED,
                pageable).getTotalElements()).isEqualTo(1);
        assertThat(invoices.search(UUID.randomUUID(), null, null, pageable).getTotalElements()).isZero();
    }

    @Test
    void savesPaymentsAgainstAnInvoiceNewestFirst() {
        var invoice = invoices.saveAndFlush(newInvoice(1, "6000000"));
        payments.saveAndFlush(new Payment(invoice, new BigDecimal("2000000"),
                BillingConstants.PaymentMethod.MANUAL, null, BillingConstants.PaymentStatus.SUCCESS));
        payments.saveAndFlush(new Payment(invoice, new BigDecimal("4000000"),
                BillingConstants.PaymentMethod.MOMO, "order-newest", BillingConstants.PaymentStatus.PENDING));

        var history = payments.findAllByInvoice_IdOrderByCreatedAtDesc(invoice.getId());

        assertThat(history).hasSize(2);
        assertThat(payments.findByGatewayTransactionId("order-newest")).isPresent();
        assertThat(payments.findByGatewayTransactionId("order-unknown")).isEmpty();
    }

    /** Hai bản ghi MANUAL cùng gatewayTransactionId = NULL phải cùng tồn tại - unique index trên cột
     * nullable của Postgres không coi NULL là trùng nhau, và đó chính là hành vi cần. */
    @Test
    void allowsManyManualPaymentsWithoutAGatewayTransactionId() {
        var invoice = invoices.saveAndFlush(newInvoice(1, "6000000"));
        payments.saveAndFlush(new Payment(invoice, new BigDecimal("1000000"),
                BillingConstants.PaymentMethod.MANUAL, null, BillingConstants.PaymentStatus.SUCCESS));

        payments.saveAndFlush(new Payment(invoice, new BigDecimal("1000000"),
                BillingConstants.PaymentMethod.MANUAL, null, BillingConstants.PaymentStatus.SUCCESS));

        assertThat(payments.findAllByInvoice_IdOrderByCreatedAtDesc(invoice.getId())).hasSize(2);
    }

    /** Lớp phòng thủ cho Review Focus #4 ở tầng DB: một orderId chỉ sinh được một bản ghi Payment,
     * nên callback gọi lại không thể tạo thêm bản ghi thứ hai cho cùng giao dịch. */
    @Test
    void rejectsADuplicateGatewayTransactionIdAtDatabaseLevel() {
        var invoice = invoices.saveAndFlush(newInvoice(1, "6000000"));
        payments.saveAndFlush(new Payment(invoice, new BigDecimal("6000000"),
                BillingConstants.PaymentMethod.VNPAY, "order-dup", BillingConstants.PaymentStatus.PENDING));

        assertThatThrownBy(() -> payments.saveAndFlush(new Payment(invoice, new BigDecimal("6000000"),
                BillingConstants.PaymentMethod.VNPAY, "order-dup", BillingConstants.PaymentStatus.PENDING)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
