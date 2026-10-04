package com.eduerp.modules.billing.internal.repository;

import com.eduerp.modules.billing.internal.model.Combo;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ComboRepository extends JpaRepository<Combo, UUID> {

    /**
     * Filter optional duy nhất (spec mục 6 {@code ListCombos}). Một query với {@code :p IS NULL}
     * thay vì hai nhánh if trong usecase - giữ complexity ở 1, mirror
     * {@code InvoiceRepository.search}. {@code ComboRepositoryIT.searchFiltersByOptional...} phủ cả
     * hai nhánh nên lỗi suy kiểu tham số null (nếu có) đỏ ngay ở test.
     */
    @Query("""
            SELECT c FROM Combo c
            WHERE (:studentProfileId IS NULL OR c.studentProfileId = :studentProfileId)
            """)
    Page<Combo> search(@Param("studentProfileId") UUID studentProfileId, Pageable pageable);
}
