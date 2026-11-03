package com.eduerp.modules.billing.usecase;

import com.eduerp.modules.billing.internal.access.BillingBranchGuard;
import com.eduerp.modules.billing.dto.ComboResponse;
import com.eduerp.modules.billing.internal.model.Combo;
import com.eduerp.modules.billing.internal.repository.ComboRepository;
import com.eduerp.modules.students.StudentsManagement;
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
 *
 * <p>Họ tên học viên thuộc module students nên không JOIN được ở {@code ComboRepository}: gom id của
 * cả trang rồi tra một lần qua facade, không tra riêng từng combo.
 */
@Service
public class ListCombos {

    private final ComboRepository combos;

    private final BillingBranchGuard branchGuard;

    private final StudentsManagement students;

    ListCombos(ComboRepository combos, BillingBranchGuard branchGuard, StudentsManagement students) {
        this.combos = combos;
        this.branchGuard = branchGuard;
        this.students = students;
    }

    @Transactional(readOnly = true)
    public PageResponse<ComboResponse> execute(UUID actorAccountId, Pageable pageable, UUID studentProfileId,
            UUID requestedBranchId) {
        var page = combos.search(studentProfileId, branchGuard.listFilter(actorAccountId, requestedBranchId), pageable);
        var names = students.displayNamesOf(page.stream().map(Combo::getStudentProfileId).distinct().toList());
        return PageResponse.of(page.map(combo -> CreateCombo.toResponse(combo, names.get(combo.getStudentProfileId()))));
    }
}
