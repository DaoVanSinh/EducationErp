import { NAV_ITEMS } from "@/app/layouts/nav-items";
import { usePermissions } from "@/entities/permission";
import { APP_ROUTE } from "@/shared/constants/app-routes";
import { cx } from "@/shared/lib/class-names";
import { MOTION_SPRING } from "@/shared/lib/motion";
import { m } from "framer-motion";
import { LayoutDashboard, ShieldCheck, UserCircle, Users } from "lucide-react";
import type { ReactNode } from "react";
import { NavLink } from "react-router-dom";

/** Icon của từng mục, khoá theo đường dẫn để cấu hình điều hướng không phải mang theo JSX. */
const NAV_ICON: Record<string, ReactNode> = {
  [APP_ROUTE.dashboard]: <LayoutDashboard size={18} aria-hidden />,
  [APP_ROUTE.accounts]: <Users size={18} aria-hidden />,
  [APP_ROUTE.roles]: <ShieldCheck size={18} aria-hidden />,
  [APP_ROUTE.profile]: <UserCircle size={18} aria-hidden />,
};

export function AppNav({ onNavigate }: { readonly onNavigate?: () => void }) {
  const permissions = usePermissions();
  const visibleItems = NAV_ITEMS.filter(
    (item) => item.requirement === undefined || permissions.allows(item.requirement),
  );

  return (
    <nav className="flex flex-col gap-1">
      {visibleItems.map((item) => (
        <NavLink key={item.path} to={item.path} end onClick={onNavigate}>
          {({ isActive }) => (
            <span
              className={cx(
                "relative flex items-center gap-3 rounded-2xl px-3 py-2.5 text-sm transition-colors",
                isActive ? "text-mist-100" : "text-mist-400 hover:text-mist-100",
              )}
            >
              {isActive ? (
                <m.span
                  layoutId="nav-active"
                  transition={MOTION_SPRING}
                  className="absolute inset-0 rounded-2xl border border-white/15 bg-white/8"
                />
              ) : null}
              <span className="relative">{NAV_ICON[item.path]}</span>
              <span className="relative">{item.label}</span>
            </span>
          )}
        </NavLink>
      ))}
    </nav>
  );
}
