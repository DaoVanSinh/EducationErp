import { MOTION_SPRING } from "@/shared/lib/motion";
import { m } from "framer-motion";
import { KeyRound, Lock, ShieldCheck, Users } from "lucide-react";

export interface RoleKpiBannerProps {
  readonly totalRoles: number;
  readonly totalGroups: number;
  readonly totalPermissions: number;
  readonly totalBranches: number;
}

const METRICS = [
  {
    key: "roles",
    label: "Vai trò",
    icon: <ShieldCheck size={20} aria-hidden />,
    color: "from-[#FF8C42] to-[#FF5E62]",
    subtext: "Vai trò trong hệ thống",
  },
  {
    key: "groups",
    label: "Nhóm quyền",
    icon: <KeyRound size={20} aria-hidden />,
    color: "from-amber-400 to-orange-500",
    subtext: "Gói quyền phân bổ",
  },
  {
    key: "permissions",
    label: "Quyền hạn",
    icon: <Lock size={20} aria-hidden />,
    color: "from-emerald-400 to-teal-500",
    subtext: "Hành động theo phân hệ",
  },
  {
    key: "branches",
    label: "Chi nhánh",
    icon: <Users size={20} aria-hidden />,
    color: "from-blue-400 to-indigo-500",
    subtext: "Phạm vi hoạt động",
  },
] as const;

export function RoleKpiBanner({
  totalRoles,
  totalGroups,
  totalPermissions,
  totalBranches,
}: RoleKpiBannerProps) {
  const counts: Record<string, number> = {
    roles: totalRoles,
    groups: totalGroups,
    permissions: totalPermissions,
    branches: totalBranches,
  };

  return (
    <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
      {METRICS.map((metric, index) => (
        <m.div
          key={metric.key}
          initial={{ opacity: 0, y: 12 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ ...MOTION_SPRING, delay: index * 0.05 }}
          whileHover={{ y: -3 }}
          className="glass-deep glass-sheen relative flex items-center gap-4 rounded-3xl p-5 shadow-sm transition-all hover:shadow-md hover:border-orange-300/60"
        >
          <div
            className={`flex size-12 shrink-0 items-center justify-center rounded-2xl bg-gradient-to-br ${metric.color} text-white shadow-md shadow-orange-500/20`}
          >
            {metric.icon}
          </div>
          <div className="min-w-0 flex-1">
            <p className="text-xs font-semibold tracking-wider text-slate-500 uppercase truncate">
              {metric.label}
            </p>
            <p className="mt-1 text-2xl font-extrabold text-slate-900 tabular-nums">
              {counts[metric.key]}
            </p>
            <p className="text-[11px] font-medium text-slate-400 truncate">{metric.subtext}</p>
          </div>
        </m.div>
      ))}
    </div>
  );
}
