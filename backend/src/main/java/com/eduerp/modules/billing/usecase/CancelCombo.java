package com.eduerp.modules.billing.usecase;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.billing.ComboNotFoundException;
import com.eduerp.modules.billing.internal.TuitionInvoiceIssuer;
import com.eduerp.modules.billing.internal.access.BillingBranchGuard;
import com.eduerp.modules.billing.internal.repository.ComboRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Huỷ một combo gộp nhầm: chỉ khi chưa có giao dịch nào, rồi các ghi danh còn học được lập lại hoá đơn giá thường.
 * Dùng quyền {@code UPDATE_INVOICE}, không dùng {@code Actions.DELETE}: toàn hệ thống chưa dùng DELETE ở resource nào.
 */
@Service
public class CancelCombo {

    private final ComboRepository combos;
    private final BillingBranchGuard branchGuard;
    private final TuitionInvoiceIssuer issuer;

    CancelCombo(ComboRepository combos, BillingBranchGuard branchGuard, TuitionInvoiceIssuer issuer) {
        this.combos = combos;
        this.branchGuard = branchGuard;
        this.issuer = issuer;
    }

    @Transactional
    public void execute(UUID comboId, UUID actorAccountId, UUID actorBranchId) {
        var combo = combos.findById(comboId).orElseThrow(() -> new ComboNotFoundException(comboId));
        branchGuard.require(actorAccountId, AccessConstants.Actions.UPDATE, combo.getBranchId());
        issuer.dissolveCombo(combo, actorAccountId, actorBranchId);
    }
}
