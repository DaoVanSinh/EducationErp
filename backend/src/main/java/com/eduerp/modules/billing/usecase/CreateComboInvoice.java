package com.eduerp.modules.billing.usecase;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.billing.ComboAlreadyInvoicedException;
import com.eduerp.modules.billing.ComboNotFoundException;
import com.eduerp.modules.billing.dto.CreateComboInvoiceRequest;
import com.eduerp.modules.billing.dto.InvoiceResponse;
import com.eduerp.modules.billing.internal.TuitionInvoiceIssuer;
import com.eduerp.modules.billing.internal.access.BillingBranchGuard;
import com.eduerp.modules.billing.internal.repository.ComboRepository;
import com.eduerp.modules.billing.internal.response.InvoiceResponseAssembler;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Combo tự có hoá đơn khi được lập, nên thao tác này chỉ lập lại sau khi hoá đơn bị huỷ, hoặc phần chưa lập của combo
 * chia đợt cũ. Số tiền là phần còn lại của tổng sau giảm giá, thu đủ một lần.
 */
@Service
public class CreateComboInvoice {

    private final ComboRepository combos;
    private final BillingBranchGuard branchGuard;
    private final TuitionInvoiceIssuer issuer;
    private final InvoiceResponseAssembler assembler;

    CreateComboInvoice(ComboRepository combos, BillingBranchGuard branchGuard, TuitionInvoiceIssuer issuer,
            InvoiceResponseAssembler assembler) {
        this.combos = combos;
        this.branchGuard = branchGuard;
        this.issuer = issuer;
        this.assembler = assembler;
    }

    @Transactional
    public InvoiceResponse execute(UUID actorAccountId, UUID actorBranchId, CreateComboInvoiceRequest request) {
        var comboId = request.comboId();
        var combo = combos.findById(comboId).orElseThrow(() -> new ComboNotFoundException(comboId));
        branchGuard.require(actorAccountId, AccessConstants.Actions.CREATE, combo.getBranchId());
        try {
            var saved = issuer.issueForCombo(combo, actorAccountId, actorBranchId)
                    .orElseThrow(() -> new ComboAlreadyInvoicedException(comboId));
            return assembler.toResponse(saved);
        } catch (DataIntegrityViolationException raceLostToAnotherRequest) {
            throw new ComboAlreadyInvoicedException(comboId);
        }
    }
}
