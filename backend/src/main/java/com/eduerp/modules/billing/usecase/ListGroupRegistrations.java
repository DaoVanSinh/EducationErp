package com.eduerp.modules.billing.usecase;

import com.eduerp.modules.billing.dto.GroupRegistrationResponse;
import com.eduerp.modules.billing.internal.access.BillingBranchGuard;
import com.eduerp.modules.billing.internal.repository.GroupRegistrationRepository;
import com.eduerp.shared.PageResponse;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Filter optional dồn vào query ở repository; thành viên nạp gộp nhờ {@code @BatchSize} (mirror ListCombos). */
@Service
public class ListGroupRegistrations {

    private final GroupRegistrationRepository groups;
    private final BillingBranchGuard branchGuard;

    ListGroupRegistrations(GroupRegistrationRepository groups, BillingBranchGuard branchGuard) {
        this.groups = groups;
        this.branchGuard = branchGuard;
    }

    @Transactional(readOnly = true)
    public PageResponse<GroupRegistrationResponse> execute(UUID actorAccountId, Pageable pageable, UUID courseId) {
        return PageResponse.of(groups.search(courseId, branchGuard.listFilter(actorAccountId), pageable)
                .map(CreateGroupRegistration::toResponse));
    }
}
