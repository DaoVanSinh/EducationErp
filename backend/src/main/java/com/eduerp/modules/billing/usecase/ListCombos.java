package com.eduerp.modules.billing.usecase;

import com.eduerp.modules.billing.dto.ComboResponse;
import com.eduerp.modules.billing.internal.repository.ComboRepository;
import com.eduerp.shared.PageResponse;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Filter optional dồn vào query ở repository - usecase không rẽ nhánh nào (complexity 1).
 *
 * <p>{@code CreateCombo.toResponse} đọc {@code combo.getEnrollments().size()}, nên mỗi combo trong
 * trang cần danh sách con. {@code @BatchSize(50)} trên {@code Combo.enrollments} gộp chúng thành một
 * câu IN duy nhất thay vì N câu SELECT (mirror cách {@code ListClasses} xử lý lịch học).
 */
@Service
public class ListCombos {

    private final ComboRepository combos;

    ListCombos(ComboRepository combos) {
        this.combos = combos;
    }

    @Transactional(readOnly = true)
    public PageResponse<ComboResponse> execute(Pageable pageable, UUID studentProfileId) {
        return PageResponse.of(combos.search(studentProfileId, pageable).map(CreateCombo::toResponse));
    }
}
