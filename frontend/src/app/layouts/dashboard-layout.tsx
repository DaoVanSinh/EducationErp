import { AppNav } from "@/app/ui/app-nav";
import { SessionCard } from "@/app/ui/session-card";
import type { Session } from "@/entities/account";
import { cx } from "@/shared/lib/class-names";
import { FADE_IN, MOTION_SPRING } from "@/shared/lib/motion";
import { MorphToggleIcon } from "@/shared/ui/morph-toggle-icon";
import { AnimatePresence, m } from "framer-motion";
import { Menu, X } from "lucide";
import { GraduationCap, PanelLeftClose, PanelLeftOpen, X as XIcon } from "lucide-react";
import { useState } from "react";
import { Outlet, useLocation } from "react-router-dom";

export function DashboardLayout({ session }: { readonly session: Session }) {
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const location = useLocation();

  return (
    <div
      className={cx(
        "min-h-dvh transition-all duration-300",
        sidebarCollapsed
          ? "lg:grid lg:grid-cols-[5rem_minmax(0,1fr)]"
          : "lg:grid lg:grid-cols-[17.5rem_minmax(0,1fr)]",
      )}
    >
      {/* Mobile Sticky Header */}
      <header className="sticky top-0 z-30 flex items-center justify-between gap-3 border-b border-white/80 bg-white/85 px-4 py-3 backdrop-blur-xl shadow-xs lg:hidden">
        <div className="flex items-center gap-2.5">
          <div className="flex size-9 items-center justify-center rounded-xl bg-gradient-to-br from-[#FF8C42] to-[#FF5E62] text-white shadow-sm shadow-orange-500/25">
            <GraduationCap size={20} aria-hidden />
          </div>
          <div className="flex flex-col">
            <span className="text-sm font-bold tracking-tight text-slate-900 leading-none">EduERP</span>
            <span className="text-[10px] font-semibold text-orange-600 tracking-wider uppercase mt-0.5">
              Quản trị đào tạo
            </span>
          </div>
        </div>

        <button
          type="button"
          onClick={() => setDrawerOpen((prev) => !prev)}
          className="rounded-xl p-2 text-slate-600 transition-colors hover:text-orange-600 hover:bg-orange-50/60 cursor-pointer"
        >
          <MorphToggleIcon
            inactive={Menu}
            active={X}
            isActive={drawerOpen}
            label={drawerOpen ? "Đóng menu" : "Mở menu"}
          />
        </button>
      </header>

      {/* Desktop Liquid Glass Sidebar */}
      <aside
        className={cx(
          "glass hidden flex-col justify-between rounded-none border-y-0 border-l-0 border-r border-white/80 bg-white/70 p-4 backdrop-blur-xl shadow-sm lg:flex transition-all duration-300",
          sidebarCollapsed ? "w-20 items-center px-2" : "w-[17.5rem]",
        )}
      >
        <div className="flex flex-col gap-4 w-full">
          {/* Sidebar Top: Logo + Toggle */}
          {sidebarCollapsed ? (
            <div className="flex items-center justify-center border-b border-slate-100/90 pb-3 w-full">
              <button
                type="button"
                onClick={() => setSidebarCollapsed(false)}
                title="Mở rộng thanh bên"
                aria-label="Mở rộng thanh bên"
                className="group relative flex size-10 items-center justify-center rounded-2xl bg-gradient-to-br from-[#FF8C42] to-[#FF5E62] text-white shadow-md shadow-orange-500/25 hover:shadow-orange-500/40 hover:scale-105 transition-all cursor-pointer"
              >
                <GraduationCap
                  size={20}
                  className="transition-transform group-hover:scale-75 group-hover:opacity-0"
                  aria-hidden
                />
                <PanelLeftOpen
                  size={18}
                  className="absolute inset-0 m-auto opacity-0 transition-opacity group-hover:opacity-100"
                  aria-hidden
                />
              </button>
            </div>
          ) : (
            <div className="flex items-center justify-between border-b border-slate-100/90 pb-3 w-full">
              <div className="flex items-center gap-2.5 min-w-0">
                <div className="flex size-9 shrink-0 items-center justify-center rounded-xl bg-gradient-to-br from-[#FF8C42] to-[#FF5E62] text-white shadow-sm shadow-orange-500/25">
                  <GraduationCap size={20} aria-hidden />
                </div>
                <div className="flex flex-col min-w-0">
                  <div className="flex items-center gap-1.5">
                    <span className="text-base font-extrabold tracking-tight text-slate-900 truncate">
                      EduERP
                    </span>
                    <span className="rounded-md bg-orange-100/80 px-1.5 py-0.2 text-[9px] font-bold text-orange-700">
                      v1.2
                    </span>
                  </div>
                  <span className="text-[10px] font-semibold uppercase tracking-wider text-orange-600 truncate">
                    Quản trị đào tạo
                  </span>
                </div>
              </div>

              {/* Collapse Toggle */}
              <button
                type="button"
                onClick={() => setSidebarCollapsed(true)}
                title="Thu gọn thanh bên"
                aria-label="Thu gọn thanh bên"
                className="flex size-7 shrink-0 items-center justify-center rounded-lg text-slate-400 hover:bg-orange-50 hover:text-orange-600 transition-colors cursor-pointer"
              >
                <PanelLeftClose size={16} aria-hidden />
              </button>
            </div>
          )}

          {/* Active Branch Indicator */}
          {!sidebarCollapsed ? (
            <div className="flex items-center gap-2 rounded-xl border border-slate-200/70 bg-white/80 px-3 py-1.5 text-xs font-semibold text-slate-700 shadow-2xs">
              <span className="size-2 rounded-full bg-emerald-500 animate-pulse shrink-0" />
              <span className="truncate">Chi nhánh chính · Hà Nội</span>
            </div>
          ) : null}

          {/* Navigation Links */}
          <div className="w-full">
            <AppNav collapsed={sidebarCollapsed} />
          </div>
        </div>

        {/* Sidebar Bottom: Status Widget + User Session */}
        <div className="flex flex-col gap-3 w-full pt-4">
          {!sidebarCollapsed ? (
            <div className="rounded-2xl border border-orange-100/80 bg-gradient-to-br from-orange-50/70 via-white/80 to-rose-50/40 p-3 shadow-2xs">
              <div className="flex items-center justify-between">
                <span className="text-[11px] font-bold text-slate-700">RBAC Hoạt động</span>
                <span className="size-2 rounded-full bg-emerald-500 animate-pulse" />
              </div>
              <p className="mt-1 text-[10px] text-slate-500 leading-snug">
                Phân quyền linh hoạt theo vai trò & nhóm quyền.
              </p>
            </div>
          ) : null}

          <SessionCard session={session} collapsed={sidebarCollapsed} />
        </div>
      </aside>

      {/* Mobile Drawer */}
      <AnimatePresence>
        {drawerOpen ? (
          <m.div
            variants={FADE_IN}
            initial="hidden"
            animate="visible"
            exit="hidden"
            className="fixed inset-0 z-40 lg:hidden"
          >
            <button
              type="button"
              aria-label="Đóng menu"
              onClick={() => setDrawerOpen(false)}
              className="absolute inset-0 cursor-default bg-slate-900/40 backdrop-blur-sm"
            />
            <m.aside
              initial={{ x: -280 }}
              animate={{ x: 0 }}
              exit={{ x: -280 }}
              transition={MOTION_SPRING}
              className="glass-raised relative flex h-full w-80 max-w-[85vw] flex-col justify-between p-5 shadow-2xl overflow-y-auto"
            >
              <div className="flex flex-col gap-5">
                <div className="flex items-center justify-between border-b border-slate-100 pb-3">
                  <div className="flex items-center gap-2.5">
                    <div className="flex size-9 items-center justify-center rounded-xl bg-gradient-to-br from-[#FF8C42] to-[#FF5E62] text-white shadow-sm shadow-orange-500/25">
                      <GraduationCap size={20} aria-hidden />
                    </div>
                    <div className="flex flex-col">
                      <span className="text-base font-extrabold tracking-tight text-slate-900">
                        EduERP
                      </span>
                      <span className="text-[10px] font-semibold uppercase tracking-wider text-orange-600">
                        Quản trị đào tạo
                      </span>
                    </div>
                  </div>
                  <button
                    type="button"
                    onClick={() => setDrawerOpen(false)}
                    aria-label="Đóng menu"
                    className="p-1 text-slate-400 hover:text-slate-700"
                  >
                    <XIcon size={18} />
                  </button>
                </div>

                <div className="flex items-center gap-2 rounded-xl border border-slate-200/70 bg-white/80 px-3 py-1.5 text-xs font-semibold text-slate-700">
                  <span className="size-2 rounded-full bg-emerald-500 animate-pulse shrink-0" />
                  <span className="truncate">Chi nhánh chính · Hà Nội</span>
                </div>

                <AppNav onNavigate={() => setDrawerOpen(false)} />
              </div>

              <div className="pt-4">
                <SessionCard session={session} />
              </div>
            </m.aside>
          </m.div>
        ) : null}
      </AnimatePresence>

      {/* Main Content Area */}
      <main className="w-full min-w-0 overflow-x-hidden px-3.5 py-4 sm:px-6 sm:py-6 lg:px-8 lg:py-8">
        <AnimatePresence mode="wait">
          <m.div
            key={location.pathname}
            variants={FADE_IN}
            initial="hidden"
            animate="visible"
            exit="hidden"
            transition={{ duration: 0.16 }}
            className="w-full min-w-0"
          >
            <Outlet />
          </m.div>
        </AnimatePresence>
      </main>
    </div>
  );
}
