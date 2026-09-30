import type { PermissionOption } from "@/entities/rbac-catalog";
import {
  ACTION,
  ACTION_LABEL,
  PERMISSION_SCOPE,
  PERMISSION_SCOPE_LABEL,
  RESOURCE_LABEL,
  type PermissionScope,
} from "@/shared/constants/permissions";
import { cx } from "@/shared/lib/class-names";
import { Square, SquareCheck } from "lucide";
import { MorphIcon } from "morphicons/react";

export interface PermissionScopeRowProps {
  readonly permission: PermissionOption;
  readonly label: string;
  readonly checked: boolean;
  readonly scope?: PermissionScope;
  readonly onToggle: () => void;
  readonly onChangeScope: (scope: PermissionScope) => void;
}

const SCOPES: readonly PermissionScope[] = [
  PERMISSION_SCOPE.personal,
  PERMISSION_SCOPE.branch,
  PERMISSION_SCOPE.organization,
];

const ACTION_TONE: Record<string, string> = {
  [ACTION.read]: "bg-sky-50 text-sky-700 border-sky-200/70",
  [ACTION.create]: "bg-emerald-50 text-emerald-700 border-emerald-200/70",
  [ACTION.update]: "bg-amber-50 text-amber-700 border-amber-200/70",
  [ACTION.delete]: "bg-rose-50 text-rose-700 border-rose-200/70",
  [ACTION.approve]: "bg-purple-50 text-purple-700 border-purple-200/70",
};

export function PermissionScopeRow({
  permission,
  label,
  checked,
  scope,
  onToggle,
  onChangeScope,
}: PermissionScopeRowProps) {
  const resourceLabel = RESOURCE_LABEL[permission.resource] ?? permission.resource;
  const actionLabel = ACTION_LABEL[permission.action] ?? permission.action;
  const actionBadge = ACTION_TONE[permission.action] ?? "bg-slate-100 text-slate-700 border-slate-200";

  return (
    <div
      className={cx(
        "group flex flex-col sm:flex-row sm:items-center justify-between gap-3 rounded-2xl border p-3.5 transition-all duration-200",
        checked
          ? "border-orange-400 bg-gradient-to-r from-orange-50/70 via-white/95 to-rose-50/30 shadow-xs ring-2 ring-orange-300/30"
          : "glass-deep glass-sheen border-slate-200/80 hover:border-orange-200 hover:bg-white/90",
      )}
    >
      {/* Clickable Left Header */}
      <button
        type="button"
        role="checkbox"
        aria-checked={checked}
        onClick={onToggle}
        className="flex min-w-0 flex-1 items-center gap-3 text-left cursor-pointer"
      >
        <span
          className={cx(
            "flex size-7 shrink-0 items-center justify-center rounded-xl transition-all shadow-2xs",
            checked
              ? "bg-gradient-to-br from-[#FF8C42] to-[#FF5E62] text-white shadow-orange-500/25"
              : "border border-slate-200 bg-white/90 text-slate-400 group-hover:border-orange-300 group-hover:text-orange-500",
          )}
        >
          <MorphIcon
            icon={checked ? SquareCheck : Square}
            spring="snappy"
            reducedMotion="user"
            size={18}
            strokeWidth={checked ? 2.2 : 1.75}
            label={checked ? "Đã chọn" : "Chưa chọn"}
          />
        </span>

        <div className="min-w-0">
          <div className="flex items-center gap-2">
            <h4 className="truncate text-sm font-bold text-slate-900">{label}</h4>
          </div>
          <div className="mt-1 flex items-center gap-1.5 flex-wrap">
            <span className="rounded-md border border-slate-200/80 bg-slate-100/80 px-2 py-0.5 text-[11px] font-semibold text-slate-600">
              {resourceLabel}
            </span>
            <span className={cx("rounded-md border px-2 py-0.5 text-[11px] font-bold uppercase", actionBadge)}>
              {actionLabel}
            </span>
          </div>
        </div>
      </button>

      {/* Right Scope Segmented Controller */}
      {checked ? (
        <div className="flex items-center gap-1.5 self-end sm:self-center shrink-0">
          <span className="text-[11px] font-semibold text-slate-400 uppercase tracking-wider hidden md:inline">
            Phạm vi:
          </span>
          <div className="flex items-center gap-1 rounded-xl border border-orange-200/70 bg-white/90 p-1 shadow-2xs">
            {SCOPES.map((sc) => {
              const isActive = scope === sc;
              return (
                <button
                  key={sc}
                  type="button"
                  onClick={() => onChangeScope(sc)}
                  className={cx(
                    "rounded-lg px-2.5 py-1 text-xs font-semibold transition-all duration-150 cursor-pointer",
                    isActive
                      ? "bg-gradient-to-r from-[#FF8C42] to-[#FF5E62] text-white shadow-xs"
                      : "text-slate-600 hover:bg-slate-100 hover:text-slate-900",
                  )}
                >
                  {PERMISSION_SCOPE_LABEL[sc]}
                </button>
              );
            })}
          </div>
        </div>
      ) : null}
    </div>
  );
}
