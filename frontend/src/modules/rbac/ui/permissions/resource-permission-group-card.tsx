import type { PermissionOption } from "@/entities/rbac-catalog";
import { ACTION, ACTION_LABEL, RESOURCE, RESOURCE_LABEL } from "@/shared/constants/permissions";
import { cx } from "@/shared/lib/class-names";
import { GlassPanel } from "@/shared/ui/glass-panel";
import {
  Building2,
  FolderLock,
  History,
  KeyRound,
  LayoutDashboard,
  Lock,
  ShieldCheck,
  UserCheck,
  Users,
  type LucideIcon,
} from "lucide-react";

export interface ResourcePermissionGroupCardProps {
  readonly resource: string;
  readonly permissions: readonly PermissionOption[];
}

const RESOURCE_ICONS: Record<string, LucideIcon> = {
  [RESOURCE.account]: Users,
  [RESOURCE.role]: ShieldCheck,
  [RESOURCE.group]: FolderLock,
  [RESOURCE.permission]: Lock,
  [RESOURCE.permissionGroup]: KeyRound,
  [RESOURCE.branch]: Building2,
  [RESOURCE.auditLog]: History,
  [RESOURCE.dashboard]: LayoutDashboard,
};

const ACTION_COLOR_STYLES: Record<string, string> = {
  [ACTION.read]: "bg-sky-50/80 text-sky-700 border-sky-200/80",
  [ACTION.create]: "bg-emerald-50/80 text-emerald-700 border-emerald-200/80",
  [ACTION.update]: "bg-amber-50/80 text-amber-700 border-amber-200/80",
  [ACTION.delete]: "bg-rose-50/80 text-rose-700 border-rose-200/80",
  [ACTION.approve]: "bg-purple-50/80 text-purple-700 border-purple-200/80",
};

export function ResourcePermissionGroupCard({
  resource,
  permissions,
}: ResourcePermissionGroupCardProps) {
  const IconComponent = RESOURCE_ICONS[resource] ?? UserCheck;
  const resourceTitle = RESOURCE_LABEL[resource] ?? resource;

  return (
    <GlassPanel className="glass-deep glass-sheen flex flex-col gap-4 rounded-3xl p-5 shadow-sm transition-all hover:shadow-md hover:border-orange-200/80">
      <div className="flex items-center justify-between border-b border-slate-100/90 pb-3">
        <div className="flex items-center gap-3">
          <div className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-gradient-to-br from-[#FF8C42] to-[#FF5E62] text-white shadow-xs">
            <IconComponent size={18} aria-hidden />
          </div>
          <div>
            <h4 className="text-sm font-bold text-slate-900">{resourceTitle}</h4>
            <p className="text-[11px] font-mono text-slate-400 uppercase">{resource}</p>
          </div>
        </div>
        <span className="rounded-full bg-orange-50 px-2.5 py-0.5 text-xs font-bold text-orange-600 border border-orange-100">
          {permissions.length} hành động
        </span>
      </div>

      <div className="flex flex-wrap gap-2">
        {permissions.map((item) => {
          const actionText = ACTION_LABEL[item.action] ?? item.action;
          const badgeStyle = ACTION_COLOR_STYLES[item.action] ?? "bg-slate-100 text-slate-700 border-slate-200";

          return (
            <div
              key={item.id}
              className={cx(
                "flex items-center gap-1.5 rounded-xl border px-3 py-1.5 text-xs font-semibold shadow-2xs transition-transform hover:scale-105",
                badgeStyle,
              )}
            >
              <span className="size-1.5 rounded-full bg-current opacity-70" />
              <span>{actionText}</span>
              <span className="text-[10px] opacity-60 font-mono">({item.action})</span>
            </div>
          );
        })}
      </div>
    </GlassPanel>
  );
}
