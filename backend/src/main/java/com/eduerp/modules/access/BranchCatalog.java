package com.eduerp.modules.access;

import com.eduerp.shared.NamedReference;
import java.util.List;

/**
 * Cổng mà access tự khai báo để lấy danh sách chi nhánh cho danh mục RBAC, mà KHÔNG import
 * {@code com.eduerp.modules.organization.*}. Module organization cung cấp bean implement interface
 * này (Dependency Inversion) — chiều import vẫn chỉ là organization → access (organization còn cần
 * {@link AccessConstants} cho {@code @PreAuthorize} của riêng nó), không có cạnh ngược access →
 * organization, nếu không hai module sẽ phụ thuộc vòng lẫn nhau.
 *
 * <p>Cùng kỹ thuật với {@link AccountExistenceCheck} (identity → access).
 */
public interface BranchCatalog {

    List<NamedReference> listAll();
}
