import { PermissionSet } from "@/entities/permission/model/permission-set";
import type { GrantedPermission } from "@/shared/api/schemas";
import { createContext, type ReactNode, useContext, useMemo } from "react";

const PermissionContext = createContext<PermissionSet>(PermissionSet.empty());

export interface PermissionProviderProps {
  readonly permissions: readonly GrantedPermission[];
  readonly children: ReactNode;
}

/**
 * Nguồn duy nhất cho câu hỏi "người này được làm gì". Nhận đúng danh sách quyền từ GET /api/account/me,
 * tức cùng dữ liệu mà bộ lọc xác thực của backend đọc từ Redis: đổi nhóm quyền xong, chỉ cần invalidate
 * query phiên là toàn bộ giao diện hiện/ẩn lại theo quyền mới.
 */
export function PermissionProvider({ permissions, children }: PermissionProviderProps) {
  const permissionSet = useMemo(() => new PermissionSet(permissions), [permissions]);
  return <PermissionContext.Provider value={permissionSet}>{children}</PermissionContext.Provider>;
}

export function usePermissions(): PermissionSet {
  return useContext(PermissionContext);
}
