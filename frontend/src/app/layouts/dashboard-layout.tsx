import { AppNav } from "@/app/ui/app-nav";
import { SessionCard } from "@/app/ui/session-card";
import type { Session } from "@/entities/account";
import { FADE_IN, MOTION_SPRING } from "@/shared/lib/motion";
import { MorphToggleIcon } from "@/shared/ui/morph-toggle-icon";
import { AnimatePresence, m } from "framer-motion";
import { Menu, X } from "lucide";
import { GraduationCap } from "lucide-react";
import { useState } from "react";
import { Outlet, useLocation } from "react-router-dom";

/**
 * Khung màn hình sau khi đăng nhập: thanh điều hướng cố định ở màn hình rộng, ngăn kéo ở màn hình hẹp.
 * Nội dung trang được đổi bằng AnimatePresence theo đường dẫn nên mỗi lần chuyển trang có một nhịp
 * chuyển thay vì nhảy khựng.
 */
export function DashboardLayout({ session }: { readonly session: Session }) {
  const [drawerOpen, setDrawerOpen] = useState(false);
  const location = useLocation();

  return (
    <div className="min-h-dvh lg:grid lg:grid-cols-[17rem_minmax(0,1fr)]">
      <header className="sticky top-0 z-30 flex items-center justify-between gap-3 border-b border-white/10 bg-ink-950/70 px-4 py-3 backdrop-blur-lg lg:hidden">
        <span className="flex items-center gap-2 text-mist-100">
          <GraduationCap size={20} className="text-aqua-300" aria-hidden />
          <span className="font-semibold tracking-tight">EduERP</span>
        </span>
        <button
          type="button"
          onClick={() => setDrawerOpen((previous) => !previous)}
          className="rounded-xl p-2 text-mist-300 transition-colors hover:text-mist-100"
        >
          <MorphToggleIcon
            inactive={Menu}
            active={X}
            isActive={drawerOpen}
            label={drawerOpen ? "Đóng menu" : "Mở menu"}
          />
        </button>
      </header>

      <aside className="glass hidden flex-col gap-6 rounded-none border-y-0 border-l-0 p-5 lg:flex">
        <span className="flex items-center gap-2 text-mist-100">
          <GraduationCap size={22} className="text-aqua-300" aria-hidden />
          <span className="text-lg font-semibold tracking-tight">EduERP</span>
        </span>
        <div className="flex-1">
          <AppNav />
        </div>
        <SessionCard session={session} />
      </aside>

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
              className="absolute inset-0 cursor-default bg-ink-950/70 backdrop-blur-sm"
            />
            <m.aside
              initial={{ x: -280 }}
              animate={{ x: 0 }}
              exit={{ x: -280 }}
              transition={MOTION_SPRING}
              className="glass-raised relative flex h-full w-72 flex-col gap-6 rounded-none p-5"
            >
              <div className="flex-1">
                <AppNav onNavigate={() => setDrawerOpen(false)} />
              </div>
              <SessionCard session={session} />
            </m.aside>
          </m.div>
        ) : null}
      </AnimatePresence>

      <main className="min-w-0 px-4 py-6 lg:px-8 lg:py-8">
        <AnimatePresence mode="wait">
          <m.div
            key={location.pathname}
            variants={FADE_IN}
            initial="hidden"
            animate="visible"
            exit="hidden"
            transition={{ duration: 0.16 }}
          >
            <Outlet />
          </m.div>
        </AnimatePresence>
      </main>
    </div>
  );
}
