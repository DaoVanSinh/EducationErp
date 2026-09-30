import type { NamedReference } from "@/shared/api/schemas";
import { Badge } from "@/shared/ui/badge";
import { GlassPanel } from "@/shared/ui/glass-panel";
import { m } from "framer-motion";
import { ShieldCheck } from "lucide";
import { KeyRound, ShieldCheck as ShieldCheckIcon, UserCheck } from "lucide-react";
import { MorphIcon } from "morphicons/react";

export interface RoleDetailInspectorProps {
  readonly role: NamedReference | null;
  readonly allGroups: readonly NamedReference[];
}

export function RoleDetailInspector({ role, allGroups }: RoleDetailInspectorProps) {
  if (role === null) {
    return (
      <GlassPanel className="glass-deep flex flex-col items-center justify-center p-12 text-center rounded-3xl">
        <ShieldCheckIcon size={36} className="text-slate-300" aria-hidden />
        <p className="mt-3 text-sm font-semibold text-slate-700">Chưa chọn vai trò</p>
        <p className="mt-1 text-xs text-slate-400">Chọn một vai trò từ danh sách để xem chi tiết</p>
      </GlassPanel>
    );
  }

  return (
    <GlassPanel className="glass-deep glass-sheen flex flex-col gap-5 rounded-3xl p-4 sm:p-6 shadow-md min-w-0 w-full overflow-hidden">
      {/* Header Inspector */}
      <div className="flex items-start justify-between gap-4 border-b border-slate-200/80 pb-4 min-w-0">
        <div className="flex items-center gap-3.5 min-w-0 flex-1">
          <div className="flex size-12 sm:size-13 shrink-0 items-center justify-center rounded-2xl bg-gradient-to-br from-[#FF8C42] to-[#FF5E62] text-white shadow-md shadow-orange-500/25">
            <MorphIcon
              icon={ShieldCheck}
              spring="snappy"
              reducedMotion="user"
              size={24}
              strokeWidth={2}
              label={role.name}
            />
          </div>
          <div className="min-w-0 flex-1">
            <div className="flex items-center gap-2 flex-wrap">
              <h3 className="truncate text-base sm:text-lg font-bold text-slate-900">{role.name}</h3>
              <span className="shrink-0">
                <Badge tone="accent">Vai trò</Badge>
              </span>
            </div>
            <p className="mt-0.5 text-xs text-slate-500 truncate">
              Mã hệ thống: <span className="font-mono text-slate-700">{role.id}</span>
            </p>
          </div>
        </div>
      </div>

      {/* Permission Groups Catalog */}
      <div className="flex flex-col gap-3">
        <div className="flex items-center justify-between">
          <h4 className="flex items-center gap-2 text-xs font-bold tracking-wider text-slate-600 uppercase">
            <KeyRound size={15} className="text-orange-500" aria-hidden />
            Nhóm quyền trong hệ thống ({allGroups.length})
          </h4>
          <span className="text-xs font-medium text-slate-400">Cấu hình linh hoạt</span>
        </div>

        <div className="flex flex-col gap-2">
          {allGroups.map((group) => (
            <m.div
              key={group.id}
              whileHover={{ x: 2 }}
              className="flex items-center justify-between gap-3 rounded-2xl border border-orange-200/60 bg-gradient-to-r from-orange-50/70 to-rose-50/40 p-3 shadow-2xs"
            >
              <div className="flex items-center gap-2.5">
                <div className="flex size-7 shrink-0 items-center justify-center rounded-lg bg-orange-500 text-white shadow-xs">
                  <KeyRound size={13} aria-hidden />
                </div>
                <div>
                  <p className="text-sm font-bold text-slate-800">{group.name}</p>
                  <p className="text-[11px] text-slate-500 font-mono">{group.id.slice(0, 8)}</p>
                </div>
              </div>
              <Badge tone="positive">Khả dụng</Badge>
            </m.div>
          ))}
        </div>
      </div>

      {/* Scope policy note */}
      <div className="rounded-2xl border border-slate-200/80 bg-white/70 p-4">
        <h5 className="flex items-center gap-2 text-xs font-bold text-slate-700 uppercase tracking-wide">
          <UserCheck size={15} className="text-emerald-500" aria-hidden />
          Cơ chế phân quyền
        </h5>
        <p className="mt-1.5 text-xs text-slate-500 leading-relaxed">
          Mỗi vai trò được gán động các nhóm quyền nghiệp vụ
        </p>
      </div>
    </GlassPanel>
  );
}
