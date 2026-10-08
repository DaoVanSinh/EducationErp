package com.eduerp.modules.organization.usecase;

import com.eduerp.modules.organization.BranchNotFoundException;
import com.eduerp.modules.organization.OrganizationEvents;
import com.eduerp.modules.organization.dto.UpdateBranchRequest;
import com.eduerp.modules.organization.internal.repository.BranchRepository;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpdateBranch {

    private final BranchRepository branches;
    private final ApplicationEventPublisher events;

    UpdateBranch(BranchRepository branches, ApplicationEventPublisher events) {
        this.branches = branches;
        this.events = events;
    }

    @Transactional
    public void execute(UUID branchId, UUID actorAccountId, UUID actorBranchId, UpdateBranchRequest request) {
        var branch = branches.findById(branchId).orElseThrow(() -> new BranchNotFoundException(branchId));
        branch.setName(request.name());
        branch.setAddress(request.address());
        branch.setActive(request.active());
        events.publishEvent(new OrganizationEvents.BranchUpdated(branchId, actorAccountId, actorBranchId));
    }
}
