package com.eduerp.modules.courses.internal.repository;

import com.eduerp.modules.courses.CoursesConstants;
import com.eduerp.modules.courses.internal.model.Class;
import java.util.Collection;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/**
 * Bộ lọc tuỳ chọn cho danh sách lớp. Ghép bằng Specification thay vì một câu JPQL kiểu
 * {@code (:branchId IS NULL OR ...)}: PostgreSQL không suy được kiểu của tham số UUID null trong
 * {@code ? IS NULL}, và bộ lọc nào không dùng thì không sinh điều kiện nào cả.
 */
public final class ClassSpecifications {

    private static final String BRANCH_ID = "branchId";
    private static final String TEACHER_ID = "teacherId";
    private static final String STATUS = "status";

    private ClassSpecifications() {
    }

    public static Specification<Class> filtered(UUID branchId, UUID teacherId,
            Collection<CoursesConstants.ClassStatus> statuses) {
        Specification<Class> spec = (root, query, cb) -> cb.conjunction();
        if (branchId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get(BRANCH_ID), branchId));
        }
        if (teacherId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get(TEACHER_ID), teacherId));
        }
        if (statuses != null && !statuses.isEmpty()) {
            spec = spec.and((root, query, cb) -> root.get(STATUS).in(statuses));
        }
        return spec;
    }
}
