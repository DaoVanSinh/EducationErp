import { NAV_SECTIONS, type NavItem } from "@/app/layouts/nav-items";
import { usePermissions } from "@/entities/permission";
import { APP_ROUTE } from "@/shared/constants/app-routes";
import { cx } from "@/shared/lib/class-names";
import { MOTION_SPRING } from "@/shared/lib/motion";
import { m } from "framer-motion";
import { BookOpen, Building2, CalendarDays, GraduationCap, LayoutDashboard, ShieldCheck, UserCircle, UserRoundCheck, Users } from "lucide-react";
import type { ReactNode } from "react";
import { NavLink } from "react-router-dom";

const NAV_ICON: Record<string, ReactNode> = {
  [APP_ROUTE.dashboard]: <LayoutDashboard size={18} aria-hidden />,
  [APP_ROUTE.accounts]: <Users size={18} aria-hidden />,
  [APP_ROUTE.branches]: <Building2 size={18} aria-hidden />,
  [APP_ROUTE.courses]: <BookOpen size={18} aria-hidden />,
  [APP_ROUTE.classes]: <CalendarDays size={18} aria-hidden />,
  [APP_ROUTE.teachers]: <GraduationCap size={18} aria-hidden />,
  [APP_ROUTE.students]: <UserRoundCheck size={18} aria-hidden />,
  [APP_ROUTE.roles]: <ShieldCheck size={18} aria-hidden />,
  [APP_ROUTE.profile]: <UserCircle size={18} aria-hidden />,
};

export interface AppNavProps {
  readonly onNavigate?: () => void;
  readonly collapsed?: boolean;
}

export function AppNav({ onNavigate, collapsed = false }: AppNavProps) {
  const permissions = usePermissions();

  const isVisible = (item: NavItem) =>
    item.requirement === undefined || permissions.allows(item.requirement);

  return (
    <nav className="flex flex-col gap-4">
      {NAV_SECTIONS.map((section) => {
        const visibleItems = section.items.filter(isVisible);
        if (visibleItems.length === 0) return null;

        return (
          <div key={section.title} className="flex flex-col gap-1">
            {!collapsed ? (
              <span className="px-3 py-1 text-[10px] font-bold tracking-widest text-slate-400 uppercase truncate">
                {section.title}
              </span>
            ) : null}

            {visibleItems.map((item) => (
              <NavLink
                key={item.path}
                to={item.path}
                end
                onClick={onNavigate}
                title={collapsed ? item.label : undefined}
                className="group relative block focus:outline-none"
              >
                {({ isActive }) => (
                  <span
                    className={cx(
                      "relative flex items-center gap-3 rounded-2xl transition-all duration-200",
                      collapsed ? "justify-center p-2.5" : "px-3.5 py-2.5",
                      isActive
                        ? "bg-white/95 text-orange-600 font-bold shadow-xs shadow-orange-500/10 border border-orange-200/90"
                        : "text-slate-600 font-semibold hover:bg-white/80 hover:text-slate-900 border border-transparent hover:border-slate-200/60",
                    )}
                  >
                    {/* Active Accent Left Bar */}
                    {isActive ? (
                      <m.span
                        layoutId="nav-active-bar"
                        transition={MOTION_SPRING}
                        className={cx(
                          "absolute rounded-full bg-gradient-to-b from-[#FF8C42] to-[#FF5E62]",
                          collapsed
                            ? "left-0 top-2 bottom-2 w-1"
                            : "left-1.5 top-2.5 bottom-2.5 w-1",
                        )}
                      />
                    ) : null}

                    <span
                      className={cx(
                        "relative shrink-0 transition-colors",
                        isActive
                          ? "text-orange-500"
                          : "text-slate-400 group-hover:text-slate-700",
                      )}
                    >
                      {NAV_ICON[item.path]}
                    </span>

                    {!collapsed ? (
                      <span className="relative min-w-0 flex-1 truncate text-sm">
                        {item.label}
                      </span>
                    ) : null}
                  </span>
                )}
              </NavLink>
            ))}
          </div>
        );
      })}
    </nav>
  );
}
