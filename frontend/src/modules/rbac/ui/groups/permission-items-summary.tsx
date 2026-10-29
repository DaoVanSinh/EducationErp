import type { PermissionGroupItem } from "@/entities/rbac-catalog";
import {
  ACTION_LABEL,
  PERMISSION_SCOPE,
  PERMISSION_SCOPE_LABEL,
  RESOURCE_LABEL,
  type PermissionScope,
} from "@/shared/constants/permissions";
import { Badge, type BadgeProps } from "@/shared/ui/badge";

const SCOPE_TONE: Record<PermissionScope, NonNullable<BadgeProps["tone"]>> = {
  [PERMISSION_SCOPE.personal]: "neutral",
  [PERMISSION_SCOPE.branch]: "warning",
  [PERMISSION_SCOPE.organization]: "accent",
};

interface ResourceRow {
  readonly resource: string;
  readonly items: readonly PermissionGroupItem[];
}

function groupByResource(items: readonly PermissionGroupItem[]): ResourceRow[] {
  const rows = new Map<string, PermissionGroupItem[]>();
  for (const item of items) {
    rows.set(item.resource, [...(rows.get(item.resource) ?? []), item]);
  }
  return Array.from(rows.entries()).map(([resource, grouped]) => ({ resource, items: grouped }));
}

/**
 * Nội dung THẬT của một nhóm quyền: mỗi phân hệ một dòng, mỗi thao tác một nhãn kèm phạm vi (Cá nhân / Chi
 * nhánh / Toàn tổ chức) - thay cho danh sách "Khả dụng" cũ không cho biết vai trò được làm gì.
 */
export function PermissionItemsSummary({ items }: { readonly items: readonly PermissionGroupItem[] }) {
  if (items.length === 0) {
    return <p className="text-xs text-slate-400">Nhóm quyền chưa có quyền nào.</p>;
  }
  return (
    <ul className="flex flex-col gap-1.5">
      {groupByResource(items).map((row) => (
        <li key={row.resource} className="flex flex-wrap items-center gap-1.5 text-xs">
          <span className="min-w-36 font-semibold text-slate-700">{RESOURCE_LABEL[row.resource] ?? row.resource}</span>
          {row.items.map((item) => (
            <Badge key={item.permissionId} tone={SCOPE_TONE[item.scope]}>
              {ACTION_LABEL[item.action] ?? item.action} · {PERMISSION_SCOPE_LABEL[item.scope]}
            </Badge>
          ))}
        </li>
      ))}
    </ul>
  );
}
