package com.eduerp.modules.courses.internal.repository;

import com.eduerp.modules.courses.CoursesConstants;
import com.eduerp.modules.courses.internal.model.Class;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ClassRepository extends JpaRepository<Class, UUID>, JpaSpecificationExecutor<Class> {
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

    /** Danh sách có bộ lọc (chi nhánh, giáo viên, trạng thái) - cùng entity graph, cùng lý do với {@link #findAll(Pageable)}. */
    @Override
    @EntityGraph(attributePaths = "course")
    Page<Class> findAll(Specification<Class> specification, Pageable pageable);

    /** Cổng giảng dạy - lớp của chính một giáo viên. */
    @EntityGraph(attributePaths = "course")
    Page<Class> findAllByTeacherId(UUID teacherId, Pageable pageable);

    /** Số lớp còn mở mà một giáo viên đang được phân công - chặn đổi vai trò làm mất năng lực giáo viên. */
    long countByTeacherIdAndStatusIn(UUID teacherId, Collection<CoursesConstants.ClassStatus> statuses);

    /** Ứng viên của tác vụ tự khai giảng: đang tuyển sinh và đã tới (hoặc qua) ngày khai giảng. */
    List<Class> findAllByStatusAndStartDateLessThanEqual(CoursesConstants.ClassStatus status, LocalDate date);

    /** Lớp hiển thị ở trang chủ: các lớp còn mở (đang tuyển sinh hoặc đang học). */
    @EntityGraph(attributePaths = "course")
    List<Class> findAllByStatusInOrderByCodeAsc(Collection<CoursesConstants.ClassStatus> statuses);
}
