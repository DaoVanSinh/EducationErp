import type { PermissionGroupOption } from "@/entities/rbac-catalog";
import { PermissionItemsSummary } from "@/modules/rbac/ui/groups/permission-items-summary";
import { Badge } from "@/shared/ui/badge";
import { GlassButton } from "@/shared/ui/glass-button";
import { KeyRound, Lock, Pencil } from "lucide-react";

export interface GroupItemCardProps {
  readonly group: PermissionGroupOption;
  readonly canEdit: boolean;
  readonly onEdit: (group: PermissionGroupOption) => void;
}

/** Một nhóm quyền kèm đúng danh sách (quyền, phạm vi) bên trong - bấm Sửa để đổi. */
export function GroupItemCard({ group, canEdit, onEdit }: GroupItemCardProps) {
  return (
    <div className="glass-deep glass-sheen flex flex-col gap-3 rounded-2xl border border-white/90 p-4">
      <div className="flex items-start justify-between gap-3">
        <div className="flex min-w-0 items-center gap-3">
          <div className="flex size-10 shrink-0 items-center justify-center rounded-xl border border-amber-100/80 bg-amber-50/70 text-amber-600">
            <KeyRound size={18} aria-hidden />
          </div>
          <div className="min-w-0">
            <h4 className="truncate text-sm font-bold text-slate-900">{group.name}</h4>
            <p className="truncate text-xs text-slate-500">{group.description ?? `${group.items.length} quyền`}</p>
          </div>
        </div>
        {group.locked ? (
          <Badge tone="warning">
            <Lock size={11} aria-hidden /> Chỉ xem
          </Badge>
        ) : null}
      </div>

      <PermissionItemsSummary items={group.items} />

      {canEdit && !group.locked ? (
        <div className="flex justify-end">
          <GlassButton variant="secondary" size="sm" onClick={() => onEdit(group)} icon={<Pencil size={14} aria-hidden />}>
            Sửa nhóm quyền
          </GlassButton>
        </div>
      ) : null}
    </div>
  );
}
