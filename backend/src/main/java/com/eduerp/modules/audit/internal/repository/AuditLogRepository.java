package com.eduerp.modules.audit.internal.repository;

import com.eduerp.modules.audit.internal.model.AuditLog;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    List<AuditLog> findByEntityType(String entityType, Pageable pageable);

    /**
     * Lọc cả action ngay trong câu truy vấn. Nếu chỉ lọc entityType rồi lọc action trong bộ nhớ thì
     * N dòng gần nhất của một loại entity có thể không chứa hành động nào cần tìm, và danh sách trả
     * về rỗng dù dữ liệu vẫn có.
     */
    List<AuditLog> findByEntityTypeAndActionOrderByOccurredAtDesc(String entityType, String action, Pageable pageable);
}
