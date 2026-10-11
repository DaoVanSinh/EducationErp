import { cx } from "@/shared/lib/class-names";
import { MOTION_SPRING } from "@/shared/lib/motion";
import { m } from "framer-motion";
import type { ReactNode } from "react";

export interface SegmentedTab<T extends string> {
  readonly key: T;
  readonly label: string;
  readonly icon: ReactNode;
}

export interface SegmentedTabsProps<T extends string> {
  readonly tabs: readonly SegmentedTab<T>[];
  readonly activeTab: T;
  readonly onTabChange: (tab: T) => void;
}

/**
 * Tab-bar dùng chung cho mọi trang chi tiết (giáo viên, học viên...) - cùng style/motion với
 * `RbacTabBar` của trang Roles, nhưng KHÔNG gắn chặt theo `RbacTab`/ô tìm kiếm/count riêng của
 * trang đó. `RbacTabBar` giữ nguyên, không sửa.
 */
export function SegmentedTabs<T extends string>({ tabs, activeTab, onTabChange }: SegmentedTabsProps<T>) {
  return (
    <div className="glass-deep flex items-center gap-1.5 overflow-x-auto rounded-2xl p-1.5 shadow-sm scrollbar-none">
      {tabs.map((tab) => {
        const isActive = activeTab === tab.key;
        return (
          <button
            key={tab.key}
            type="button"
            onClick={() => onTabChange(tab.key)}
            className={cx(
              "relative flex shrink-0 items-center gap-2 whitespace-nowrap rounded-xl px-3.5 py-2 text-xs font-semibold transition-colors duration-200 sm:text-sm",
              isActive ? "font-bold text-orange-600" : "text-slate-600 hover:text-slate-900",
            )}
          >
            {isActive ? (
              <m.div
                layoutId="segmented-tab-active"
                transition={MOTION_SPRING}
                className="absolute inset-0 rounded-xl border border-orange-200/90 bg-gradient-to-r from-orange-500/15 to-rose-500/10 shadow-xs"
              />
            ) : null}
            <span className={cx("relative shrink-0", isActive ? "text-orange-500" : "text-slate-400")}>
              {tab.icon}
            </span>
            <span className="relative truncate">{tab.label}</span>
          </button>
        );
      })}
    </div>
  );
}
