import type { RbacTab } from "@/modules/rbac/hooks/use-roles-page-controller";
import { cx } from "@/shared/lib/class-names";
import { MOTION_SPRING } from "@/shared/lib/motion";
import { GlassInput } from "@/shared/ui/glass-input";
import { m } from "framer-motion";
import {
  Key,
  KeyRound,
  Lock,
  LockKeyholeOpen,
  Search,
  SearchCheck,
  Shield,
  ShieldCheck,
} from "lucide";
import { MorphIcon } from "morphicons/react";

export interface RbacTabBarProps {
  readonly activeTab: RbacTab;
  readonly onTabChange: (tab: RbacTab) => void;
  readonly searchQuery: string;
  readonly onSearchChange: (query: string) => void;
  readonly roleCount: number;
  readonly groupCount: number;
  readonly permissionCount: number;
}

const TABS = [
  {
    key: "roles" as const,
    label: "Vai trò",
    inactiveIcon: Shield,
    activeIcon: ShieldCheck,
    countKey: "roleCount" as const,
  },
  {
    key: "groups" as const,
    label: "Nhóm quyền",
    inactiveIcon: Key,
    activeIcon: KeyRound,
    countKey: "groupCount" as const,
  },
  {
    key: "permissions" as const,
    label: "Toàn bộ quyền",
    inactiveIcon: Lock,
    activeIcon: LockKeyholeOpen,
    countKey: "permissionCount" as const,
  },
];

export function RbacTabBar({
  activeTab,
  onTabChange,
  searchQuery,
  onSearchChange,
  roleCount,
  groupCount,
  permissionCount,
}: RbacTabBarProps) {
  const counts = { roleCount, groupCount, permissionCount };

  return (
    <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between min-w-0 w-full">
      {/* Segmented Liquid Glass Tab bar with MorphIcon */}
      <div className="glass-deep flex items-center gap-1.5 rounded-2xl p-1.5 shadow-sm overflow-x-auto scrollbar-none max-w-full shrink-0">
        {TABS.map((tab) => {
          const isActive = activeTab === tab.key;
          return (
            <button
              key={tab.key}
              type="button"
              onClick={() => onTabChange(tab.key)}
              className={cx(
                "relative flex items-center gap-2 rounded-xl px-3.5 py-2 text-xs sm:text-sm font-semibold transition-colors duration-200 shrink-0 whitespace-nowrap cursor-pointer",
                isActive ? "text-orange-600 font-bold" : "text-slate-600 hover:text-slate-900",
              )}
            >
              {isActive ? (
                <m.div
                  layoutId="rbac-tab-active"
                  transition={MOTION_SPRING}
                  className="absolute inset-0 rounded-xl border border-orange-200/90 bg-gradient-to-r from-orange-500/15 to-rose-500/10 shadow-xs"
                />
              ) : null}
              <MorphIcon
                icon={isActive ? tab.activeIcon : tab.inactiveIcon}
                spring="snappy"
                reducedMotion="user"
                size={16}
                strokeWidth={isActive ? 2.2 : 1.75}
                className={cx(
                  "relative transition-colors shrink-0",
                  isActive ? "text-orange-500" : "text-slate-400",
                )}
                label={tab.label}
              />
              <span className="relative truncate">{tab.label}</span>
              <span
                className={cx(
                  "relative ml-0.5 rounded-full px-2 py-0.5 text-xs font-bold shrink-0",
                  isActive ? "bg-orange-500/20 text-orange-600" : "bg-slate-200/60 text-slate-500",
                )}
              >
                {counts[tab.countKey]}
              </span>
            </button>
          );
        })}
      </div>

      {/* Quick Search Field with dynamic morphing icon */}
      <div className="relative w-full sm:w-72 shrink-0">
        <GlassInput
          type="text"
          placeholder="Tìm vai trò, quyền hạn..."
          value={searchQuery}
          onChange={(e) => onSearchChange(e.target.value)}
          className="pl-10 pr-4 text-xs sm:text-sm"
        />
        <div className="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400">
          <MorphIcon
            icon={searchQuery.trim().length > 0 ? SearchCheck : Search}
            spring="snappy"
            reducedMotion="user"
            size={16}
            strokeWidth={1.8}
            label="Tìm kiếm"
          />
        </div>
      </div>
    </div>
  );
}
