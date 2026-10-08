package com.eduerp.modules.courses.internal.repository;

import com.eduerp.modules.courses.internal.model.Course;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseRepository extends JpaRepository<Course, UUID> {
    Optional<Course> findByCode(String code);

    Page<Course> findAll(Pageable pageable);

    /**
     * Truy xuất của chatbot (spec mục 3.3). Ba chi tiết đều có chủ đích:
     *
     * <p>1. {@code replace(plainto_tsquery(...)::text, '&', '|')::tsquery} - plainto_tsquery nối MỌI
     * từ bằng {@code &}, nên một câu hỏi tự nhiên ("có khóa tiếng anh giao tiếp buổi tối không") sẽ
     * đòi khóa học chứa đủ cả "có" và "không", tức luôn rỗng. Đổi sang {@code |} cho ngữ nghĩa OR,
     * rồi {@code ts_rank} + {@code LIMIT} lo phần "khớp nhiều từ thì lên trước". An toàn vì
     * plainto_tsquery đã làm sạch dấu câu và cấu hình 'simple' không bao giờ sinh lexeme chứa
     * {@code &}; câu hỏi toàn dấu câu cho tsquery RỖNG, {@code @@} trả false, danh sách rỗng.
     *
     * <p>2. {@code active = true} nằm trong SQL, không ở tầng Java (Review Focus #3).
     *
     * <p>3. Liệt kê cột tường minh thay vì {@code SELECT *}: cột {@code search_vector} là tsvector
     * và không có field nào trên entity, không đưa nó vào result set là không bao giờ phải lo
     * Hibernate đọc một kiểu nó không biết.
     */
    @Query(value = """
            WITH q AS (
                SELECT replace(plainto_tsquery('simple', :query)::text, '&', '|')::tsquery AS tsq
            )
            SELECT c.id, c.code, c.name, c.description, c.standard_session_count,
                   c.tuition_fee, c.active
            FROM courses c, q
            WHERE c.active = true
              AND c.search_vector @@ q.tsq
            ORDER BY ts_rank(c.search_vector, q.tsq) DESC, c.code ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<Course> searchActiveByFullText(@Param("query") String query, @Param("limit") int limit);
}
