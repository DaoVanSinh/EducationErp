import { usePermissions } from "@/entities/permission/model/permission-context";
import type { PermissionRequirement } from "@/entities/permission/model/permission-set";
import type { ReactNode } from "react";

export interface CanProps extends PermissionRequirement {
  readonly children: ReactNode;
  readonly fallback?: ReactNode;
}

/** Ẩn một mẩu giao diện khi người dùng không có quyền tương ứng. */
export function Can({ resource, action, scope, children, fallback = null }: CanProps) {
  const permissions = usePermissions();
  return permissions.allows({ resource, action, scope }) ? <>{children}</> : <>{fallback}</>;
}
