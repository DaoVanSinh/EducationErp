package com.eduerp.modules.courses.internal.repository;

import com.eduerp.modules.courses.internal.model.Class;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClassRepository extends JpaRepository<Class, UUID> {
    Optional<Class> findByCode(String code);

    /**
     * {@code course} nạp kèm trong câu JOIN chính qua entity graph — an toàn với {@code Pageable} vì
     * đây là quan hệ to-one (không nhân dòng, phân trang vẫn chạy ở SQL, không rơi về phân trang
     * trong bộ nhớ). {@code schedule} (to-many) cố tình không đưa vào đây, dùng {@code @BatchSize}
     * trên chính entity thay vì entity graph - đưa to-many vào entity graph kèm Pageable sẽ khiến
     * Hibernate bỏ qua LIMIT/OFFSET ở SQL và tự phân trang trong bộ nhớ (HHH90003004).
     */
    @Override
    @EntityGraph(attributePaths = "course")
    Page<Class> findAll(Pageable pageable);
}
