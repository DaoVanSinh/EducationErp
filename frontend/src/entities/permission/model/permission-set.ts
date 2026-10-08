import type { GrantedPermission } from "@/shared/api/schemas";
import {
  PERMISSION_SCOPE,
  PERMISSION_SCOPE_RANK,
  type PermissionScope,
} from "@/shared/constants/permissions";

export interface PermissionRequirement {
  readonly resource: string;
  readonly action: string;
  /** Scope tối thiểu. Mặc định PERSONAL, khớp ScopedPermissionEvaluator khi endpoint không ghi scope. */
  readonly scope?: PermissionScope;
}

/**
 * Quyền của người đang đăng nhập, với đúng luật mà backend dùng: một quyền ở scope rộng hơn thoả mãn
 * được yêu cầu scope hẹp hơn (ORGANIZATION ⊇ BRANCH ⊇ PERSONAL).
 *
 * Đây chỉ là lớp quyết định hiển thị. Backend vẫn kiểm tra lại bằng @PreAuthorize - nếu hai bên lệch
 * nhau thì hậu quả là một cái nút bấm vào bị 403, không phải một lỗ hổng.
 */
export class PermissionSet {
  private readonly granted: readonly GrantedPermission[];

  constructor(granted: readonly GrantedPermission[]) {
    this.granted = granted;
  }

  static empty(): PermissionSet {
    return new PermissionSet([]);
  }

  allows({ resource, action, scope = PERMISSION_SCOPE.personal }: PermissionRequirement): boolean {
    const required = PERMISSION_SCOPE_RANK[scope];
    return this.granted.some(
      (permission) =>
        permission.resource === resource &&
        permission.action === action &&
        PERMISSION_SCOPE_RANK[permission.scope] >= required,
    );
  }

  allowsAny(requirements: readonly PermissionRequirement[]): boolean {
    return requirements.some((requirement) => this.allows(requirement));
  }

  list(): readonly GrantedPermission[] {
    return this.granted;
  }
}
