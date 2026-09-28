package com.eduerp.modules.organization;

import com.eduerp.modules.organization.internal.repository.BranchRepository;
import com.eduerp.shared.NamedReference;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Facade của module organization — type DUY NHẤT mà module khác được phép gọi (rule #1). Chỉ trả
 * dữ liệu thô (tên, tồn tại hay không), không bao giờ trả entity {@code Branch} ra ngoài module.
 */
@Service
public class OrganizationManagement {

    private final BranchRepository branches;

    OrganizationManagement(BranchRepository branches) {
        this.branches = branches;
    }

    @Transactional(readOnly = true)
    public boolean exists(UUID branchId) {
        return branches.existsById(branchId);
    }

    @Transactional(readOnly = true)
    public long count() {
        return branches.count();
    }

    /**
     * Tên chi nhánh theo lô id. Trả {@code Map} chứ không phải {@code List<Branch>}: người gọi ghép
     * dữ liệu ở use case của chính họ (rule #3), không bao giờ JOIN thẳng bảng {@code branches}.
     */
    @Transactional(readOnly = true)
    public Map<UUID, String> namesOf(Collection<UUID> branchIds) {
        return branches.findAllById(branchIds).stream()
                .collect(Collectors.toMap(b -> b.getId(), b -> b.getName()));
    }

    @Transactional(readOnly = true)
    public List<NamedReference> listAll() {
        return branches.findAll(Sort.by("code")).stream()
                .map(b -> new NamedReference(b.getId(), b.getName()))
                .sorted(Comparator.comparing(NamedReference::name))
                .toList();
    }
}
