package com.eduerp.modules.billing.usecase;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.billing.BillingEvents;
import com.eduerp.modules.billing.GroupRegistrationHasInvoicesException;
import com.eduerp.modules.billing.GroupRegistrationNotFoundException;
import com.eduerp.modules.billing.internal.TuitionInvoiceIssuer;
import com.eduerp.modules.billing.internal.access.BillingBranchGuard;
import com.eduerp.modules.billing.internal.model.GroupRegistrationMember;
import com.eduerp.modules.billing.internal.repository.GroupRegistrationRepository;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Huỷ một nhóm lập nhầm: xoá cứng, các ghi danh còn học được lập lại hoá đơn giá niêm yết. Chỉ khi chưa thành viên nào
 * đóng tiền: số đã thu theo giá giảm sẽ lệch với giá mới. Quyền {@code UPDATE_INVOICE}, mirror {@code CancelCombo}.
 */
@Service
public class CancelGroupRegistration {

    private final GroupRegistrationRepository groups;
    private final ApplicationEventPublisher events;
    private final BillingBranchGuard branchGuard;
    private final TuitionInvoiceIssuer issuer;

    CancelGroupRegistration(GroupRegistrationRepository groups, ApplicationEventPublisher events,
            BillingBranchGuard branchGuard, TuitionInvoiceIssuer issuer) {
        this.groups = groups;
        this.events = events;
        this.branchGuard = branchGuard;
        this.issuer = issuer;
    }

    @Transactional
    public void execute(UUID groupId, UUID actorAccountId, UUID actorBranchId) {
        var group = groups.findById(groupId).orElseThrow(() -> new GroupRegistrationNotFoundException(groupId));
        branchGuard.require(actorAccountId, AccessConstants.Actions.UPDATE, group.getBranchId());
        var memberIds = group.getMembers().stream().map(GroupRegistrationMember::getEnrollmentId).toList();
        if (memberIds.stream().anyMatch(issuer::hasCollected)) {
            throw new GroupRegistrationHasInvoicesException(groupId);
        }
        memberIds.forEach(id -> issuer.cancelUnpaidOf(id, actorAccountId, actorBranchId));
        // Phát trước khi xoá, cùng transaction - mirror CancelCombo.
        events.publishEvent(new BillingEvents.GroupRegistrationCancelled(groupId, actorAccountId, actorBranchId));
        groups.delete(group);
        groups.flush();
        memberIds.forEach(id -> issuer.reissueIfActive(id, actorAccountId, actorBranchId));
    }
}
