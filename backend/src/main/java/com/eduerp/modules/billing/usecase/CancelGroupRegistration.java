package com.eduerp.modules.billing.usecase;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.billing.BillingConstants;
import com.eduerp.modules.billing.BillingEvents;
import com.eduerp.modules.billing.GroupRegistrationHasInvoicesException;
import com.eduerp.modules.billing.GroupRegistrationNotFoundException;
import com.eduerp.modules.billing.internal.access.BillingBranchGuard;
import com.eduerp.modules.billing.internal.repository.GroupRegistrationRepository;
import com.eduerp.modules.billing.internal.repository.InvoiceRepository;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Huỷ một nhóm lập nhầm: xoá cứng, trần học phí của các ghi danh trở về giá niêm yết. Chỉ khi chưa thành viên
 * nào có hoá đơn sống - nếu không, hoá đơn đã phát hành theo giá giảm sẽ lệch với trần mới. Quyền
 * {@code UPDATE_INVOICE}, mirror {@code CancelCombo}.
 */
@Service
public class CancelGroupRegistration {

    private final GroupRegistrationRepository groups;
    private final InvoiceRepository invoices;
    private final ApplicationEventPublisher events;
    private final BillingBranchGuard branchGuard;

    CancelGroupRegistration(GroupRegistrationRepository groups, InvoiceRepository invoices,
            ApplicationEventPublisher events, BillingBranchGuard branchGuard) {
        this.groups = groups;
        this.invoices = invoices;
        this.events = events;
        this.branchGuard = branchGuard;
    }

    @Transactional
    public void execute(UUID groupId, UUID actorAccountId, UUID actorBranchId) {
        var group = groups.findById(groupId).orElseThrow(() -> new GroupRegistrationNotFoundException(groupId));
        branchGuard.require(actorAccountId, AccessConstants.Actions.UPDATE, group.getBranchId());
        var anyInvoiced = group.getMembers().stream().anyMatch(member -> invoices.countByEnrollmentIdAndStatusNot(
                member.getEnrollmentId(), BillingConstants.InvoiceStatus.CANCELLED) > 0);
        if (anyInvoiced) {
            throw new GroupRegistrationHasInvoicesException(groupId);
        }
        // Phát trước khi xoá, cùng transaction - mirror CancelCombo.
        events.publishEvent(new BillingEvents.GroupRegistrationCancelled(groupId, actorAccountId, actorBranchId));
        groups.delete(group);
    }
}
