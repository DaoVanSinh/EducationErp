package com.eduerp.modules.organization.usecase;

import com.eduerp.modules.organization.dto.BranchResponse;
import com.eduerp.modules.organization.internal.model.Branch;
import com.eduerp.modules.organization.internal.repository.BranchRepository;
import com.eduerp.shared.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListBranches {

    private final BranchRepository branches;

    ListBranches(BranchRepository branches) {
        this.branches = branches;
    }

    @Transactional(readOnly = true)
    public PageResponse<BranchResponse> execute(Pageable pageable) {
        return PageResponse.of(branches.findAll(pageable).map(ListBranches::toResponse));
    }

    private static BranchResponse toResponse(Branch branch) {
        return new BranchResponse(branch.getId(), branch.getCode(), branch.getName(), branch.getAddress(),
                branch.isActive());
    }
}
