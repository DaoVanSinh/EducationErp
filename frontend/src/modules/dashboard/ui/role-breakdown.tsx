import type { RoleHeadcount } from "@/modules/dashboard/model/dashboard-schema";
import { formatter } from "@/shared/lib/format";
import { MOTION_SPRING_SOFT, staggerDelay } from "@/shared/lib/motion";
import { EmptyState } from "@/shared/ui/empty-state";
import { GlassPanel } from "@/shared/ui/glass-panel";
import { m } from "framer-motion";

export interface RoleBreakdownProps {
  readonly rows: readonly RoleHeadcount[];
  readonly totalAccounts: number;
}

/** Tỷ lệ tài khoản theo vai trò. Thanh ngang thay cho biểu đồ tròn: đọc được ngay cả khi chỉ có 2 vai trò. */
export function RoleBreakdown({ rows, totalAccounts }: RoleBreakdownProps) {
  return (
    <GlassPanel className="flex flex-col gap-4 shadow-sm">
      <h2 className="text-sm font-bold tracking-wider text-slate-800 uppercase">Tài khoản theo vai trò</h2>

      {rows.length === 0 ? (
        <EmptyState title="Chưa có vai trò nào được gán" />
      ) : (
        <ul className="flex flex-col gap-3.5">
          {rows.map((row, index) => {
            const ratio = totalAccounts === 0 ? 0 : row.accountCount / totalAccounts;
            return (
              <li key={row.roleCode} className="flex flex-col gap-1.5">
                <div className="flex items-baseline justify-between gap-3 text-sm">
                  <span className="font-medium text-slate-700">{row.roleName}</span>
                  <span className="font-semibold text-slate-500 tabular-nums">{formatter.count(row.accountCount)}</span>
                </div>
                <div className="h-2 overflow-hidden rounded-full bg-slate-100">
                  <m.div
                    className="h-full rounded-full bg-gradient-to-r from-[#FF8C42] via-[#FF755A] to-[#FF5E62] shadow-xs"
                    initial={{ width: 0 }}
                    animate={{ width: `${Math.round(ratio * 100)}%` }}
                    transition={{ ...MOTION_SPRING_SOFT, ...staggerDelay(index) }}
                  />
                </div>
              </li>
            );
          })}
        </ul>
      )}
    </GlassPanel>
  );
}
