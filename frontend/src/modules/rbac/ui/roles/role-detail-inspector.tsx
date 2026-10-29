import type { PermissionGroupOption, RoleOption } from "@/entities/rbac-catalog";
import { PermissionItemsSummary } from "@/modules/rbac/ui/groups/permission-items-summary";
import { Badge } from "@/shared/ui/badge";
import { EmptyState } from "@/shared/ui/empty-state";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassPanel } from "@/shared/ui/glass-panel";
import { KeyRound, Lock, Pencil, ShieldCheck, Users } from "lucide-react";

export interface RoleDetailInspectorProps {
  readonly role: RoleOption | null;
  /** Nhóm quyền MÀ VAI TRÒ NÀY ĐANG GỒM - không phải mọi nhóm quyền của hệ thống. */
  readonly roleGroups: readonly PermissionGroupOption[];
  readonly canEdit: boolean;
  readonly onEdit: (role: RoleOption) => void;
}

export function RoleDetailInspector({ role, roleGroups, canEdit, onEdit }: RoleDetailInspectorProps) {
  if (role === null) {
    return (
      <GlassPanel className="glass-deep flex flex-col items-center justify-center p-12 text-center rounded-3xl">
        <ShieldCheck size={36} className="text-slate-300" aria-hidden />
        <p className="mt-3 text-sm font-semibold text-slate-700">Chưa chọn vai trò</p>
        <p className="mt-1 text-xs text-slate-400">Chọn một vai trò từ danh sách để xem chi tiết</p>
      </GlassPanel>
    );
  }

  return (
    <GlassPanel className="glass-deep glass-sheen flex flex-col gap-5 rounded-3xl p-4 sm:p-6 shadow-md min-w-0 w-full overflow-hidden">
      <RoleHeader role={role} canEdit={canEdit} onEdit={onEdit} />

      <div className="flex flex-col gap-3">
        <h4 className="flex items-center gap-2 text-xs font-bold tracking-wider text-slate-600 uppercase">
          <KeyRound size={15} className="text-orange-500" aria-hidden />
          Nhóm quyền của vai trò ({roleGroups.length})
        </h4>
        {roleGroups.length === 0 ? (
          <EmptyState title="Vai trò chưa có nhóm quyền nào" description="Người giữ vai trò này chưa làm được gì." />
        ) : (
          roleGroups.map((group) => (
            <div key={group.id} className="rounded-2xl border border-orange-200/60 bg-white/70 p-3 shadow-2xs">
              <p className="text-sm font-bold text-slate-800">{group.name}</p>
              {group.description ? <p className="mb-2 text-xs text-slate-500">{group.description}</p> : null}
              <PermissionItemsSummary items={group.items} />
            </div>
          ))
        )}
      </div>
    </GlassPanel>
  );
}

function RoleHeader({ role, canEdit, onEdit }: Omit<RoleDetailInspectorProps, "roleGroups" | "role"> & {
  readonly role: RoleOption;
}) {
  return (
    <div className="flex flex-wrap items-start justify-between gap-4 border-b border-slate-200/80 pb-4 min-w-0">
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-center gap-2">
          <h3 className="truncate text-base sm:text-lg font-bold text-slate-900">{role.name}</h3>
          <Badge tone="accent">{role.code}</Badge>
          {role.systemDefault ? <Badge tone="neutral">Vai trò hệ thống</Badge> : null}
          {role.locked ? (
            <Badge tone="warning">
              <Lock size={11} aria-hidden /> Chỉ xem
            </Badge>
          ) : null}
        </div>
        <p className="mt-1 flex items-center gap-1.5 text-xs text-slate-500">
          <Users size={13} aria-hidden /> {role.accountCount} tài khoản đang giữ vai trò này
        </p>
        {role.locked ? (
          <p className="mt-1 text-xs text-slate-400">
            Vai trò Quản trị viên được khoá để không ai tự khoá hệ thống. Tạo vai trò mới nếu cần quản trị viên hạn chế.
          </p>
        ) : null}
      </div>
      {canEdit && !role.locked ? (
        <GlassButton variant="secondary" size="sm" onClick={() => onEdit(role)} icon={<Pencil size={14} aria-hidden />}>
          Sửa vai trò
        </GlassButton>
      ) : null}
    </div>
  );
}
