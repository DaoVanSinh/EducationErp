import { usePermissions } from "@/entities/permission/model/permission-context";
import type { PermissionRequirement } from "@/entities/permission/model/permission-set";
import { EmptyState } from "@/shared/ui/empty-state";
import { GlassPanel } from "@/shared/ui/glass-panel";
import { ShieldOff } from "lucide-react";
import type { ReactNode } from "react";

export interface RequirePermissionProps extends PermissionRequirement {
  readonly children: ReactNode;
}

/**
 * Cổng cho cả một trang. Không điều hướng đi đâu: người dùng vẫn thấy mình đang ở đâu và tại sao
 * không vào được, thay vì bị bật về trang chủ mà không hiểu vừa xảy ra chuyện gì.
 */
export function RequirePermission({ resource, action, scope, children }: RequirePermissionProps) {
  const permissions = usePermissions();

  if (permissions.allows({ resource, action, scope })) {
    return <>{children}</>;
  }

  return (
    <GlassPanel>
      <EmptyState
        icon={<ShieldOff size={28} aria-hidden />}
        title="Bạn không có quyền xem phần này"
        description="Nếu cần truy cập, hãy đề nghị quản trị viên gán thêm nhóm quyền cho tài khoản của bạn."
      />
    </GlassPanel>
  );
}
