import { usePermissions } from "@/entities/permission";
import {
  ACTION_LABEL,
  PERMISSION_SCOPE,
  PERMISSION_SCOPE_LABEL,
  RESOURCE_LABEL,
  type PermissionScope,
} from "@/shared/constants/permissions";
import { staggerDelay } from "@/shared/lib/motion";
import { Badge } from "@/shared/ui/badge";
import { EmptyState } from "@/shared/ui/empty-state";
import { GlassPanel } from "@/shared/ui/glass-panel";
import { m } from "framer-motion";

const SCOPE_TONE: Record<PermissionScope, "neutral" | "accent" | "positive"> = {
  [PERMISSION_SCOPE.personal]: "neutral",
  [PERMISSION_SCOPE.branch]: "accent",
  [PERMISSION_SCOPE.organization]: "positive",
};

/** Người dùng tự xem mình được làm gì - bớt hẳn một vòng hỏi quản trị viên khi bị 403. */
export function GrantedPermissionList() {
  const permissions = usePermissions();
  const rows = permissions.list();

  return (
    <GlassPanel className="flex flex-col gap-4 shadow-sm">
      <h2 className="text-sm font-bold tracking-wide text-slate-800 uppercase">Quyền của bạn</h2>

      {rows.length === 0 ? (
        <EmptyState title="Tài khoản chưa được cấp quyền nào" />
      ) : (
        <ul className="flex flex-wrap gap-2">
          {rows.map((permission, index) => (
            <m.li
              key={`${permission.resource}-${permission.action}-${permission.scope}`}
              initial={{ opacity: 0, scale: 0.94 }}
              animate={{ opacity: 1, scale: 1 }}
              transition={staggerDelay(index)}
            >
              <Badge tone={SCOPE_TONE[permission.scope]}>
                {ACTION_LABEL[permission.action] ?? permission.action}{" "}
                {(RESOURCE_LABEL[permission.resource] ?? permission.resource).toLowerCase()}
                <span className="opacity-70">· {PERMISSION_SCOPE_LABEL[permission.scope]}</span>
              </Badge>
            </m.li>
          ))}
        </ul>
      )}
    </GlassPanel>
  );
}
