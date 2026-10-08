package com.eduerp.modules.organization.usecase;

import com.eduerp.modules.organization.BranchCodeAlreadyExistsException;
import com.eduerp.modules.organization.OrganizationEvents;
import com.eduerp.modules.organization.dto.CreateBranchRequest;
import com.eduerp.modules.organization.internal.model.Branch;
import com.eduerp.modules.organization.internal.repository.BranchRepository;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateBranch {

    private final BranchRepository branches;
    private final ApplicationEventPublisher events;

    CreateBranch(BranchRepository branches, ApplicationEventPublisher events) {
        this.branches = branches;
        this.events = events;
    }

    @Transactional
    public UUID execute(UUID actorAccountId, UUID actorBranchId, CreateBranchRequest request) {
        if (branches.findByCode(request.code()).isPresent()) {
            throw new BranchCodeAlreadyExistsException(request.code());
        }
        var saved = branches.save(new Branch(request.code(), request.name(), request.address()));
        events.publishEvent(new OrganizationEvents.BranchCreated(saved.getId(), actorAccountId, actorBranchId));
        return saved.getId();
    }
}
