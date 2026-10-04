package com.eduerp.modules.billing.internal.repository;

import com.eduerp.modules.billing.BillingConstants;
import com.eduerp.modules.billing.internal.model.Invoice;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    /** Invoice CANCELLED không tính vào hạn mức 3 đợt (spec mục 5 bước 3). */
    long countByEnrollmentIdAndStatusNot(UUID enrollmentId, BillingConstants.InvoiceStatus status);

    /** Cũng loại CANCELLED khi cộng tổng để so với học phí (spec mục 5 bước 4). */
    List<Invoice> findAllByEnrollmentIdAndStatusNot(UUID enrollmentId, BillingConstants.InvoiceStatus status);

    /** Mirror {@code countByEnrollmentIdAndStatusNot} cho đơn vị neo combo: tối đa 3 đợt cho CẢ
     * combo, invoice CANCELLED không chiếm chỗ (spec mục 6). */
    long countByComboIdAndStatusNot(UUID comboId, BillingConstants.InvoiceStatus status);

    /** Cũng loại CANCELLED khi cộng tổng để so với {@code Combo.totalDiscountedAmount}. */
    List<Invoice> findAllByComboIdAndStatusNot(UUID comboId, BillingConstants.InvoiceStatus status);

    /** Chi tiết combo hiển thị MỌI hoá đơn của combo, kể cả đã huỷ (spec mục 6 {@code GetComboDetail}). */
    List<Invoice> findAllByComboId(UUID comboId);

    /** Review Focus #6: {@code CancelCombo} chỉ cho phép khi chưa có hoá đơn nào - đếm MỌI trạng
     * thái, kể cả CANCELLED. Một combo đã từng phát hành chứng từ tài chính thì không xoá cứng nữa. */
    long countByComboId(UUID comboId);

    List<Invoice> findAllByStatusInAndDueDateBefore(Collection<BillingConstants.InvoiceStatus> statuses,
            LocalDate dueDateBefore);

    /**
     * Ba filter đều optional (spec mục 5 {@code ListInvoices}). Một query với {@code :p IS NULL} thay
     * vì 8 nhánh if trong usecase - giữ complexity ở 1. {@code BillingRepositoryIT.searchFilters...}
     * phủ mọi tổ hợp, nên lỗi suy kiểu tham số null (nếu có) đỏ ngay ở test.
     */
    @Query("""
            SELECT i FROM Invoice i
            WHERE (:studentProfileId IS NULL OR i.studentProfileId = :studentProfileId)
              AND (:enrollmentId IS NULL OR i.enrollmentId = :enrollmentId)
              AND (:status IS NULL OR i.status = :status)
            """)
    Page<Invoice> search(@Param("studentProfileId") UUID studentProfileId,
            @Param("enrollmentId") UUID enrollmentId,
            @Param("status") BillingConstants.InvoiceStatus status, Pageable pageable);
}
