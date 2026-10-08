package com.eduerp.modules.billing.internal.repository;

import com.eduerp.modules.billing.BillingConstants;
import com.eduerp.modules.billing.internal.model.Payment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByGatewayTransactionId(String gatewayTransactionId);

    /** Lịch sử thanh toán trong chi tiết hoá đơn, mới nhất trước (spec mục 5). */
    List<Payment> findAllByInvoice_IdOrderByCreatedAtDesc(UUID invoiceId);

    /** Final review Critical #2: huỷ một hoá đơn còn một giao dịch online PENDING là mở cửa cho
     * callback đến sau hồi sinh hoá đơn đã huỷ - {@code CancelInvoice} dùng method này để chặn trước. */
    boolean existsByInvoice_IdAndStatus(UUID invoiceId, BillingConstants.PaymentStatus status);
}
