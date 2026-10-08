import { MOTION_SPRING, RISE_IN } from "@/shared/lib/motion";
import { GlassPanel } from "@/shared/ui/glass-panel";
import { m } from "framer-motion";
import { GraduationCap } from "lucide-react";
import { Outlet } from "react-router-dom";

/** Khung cho các trang chưa đăng nhập: một tấm kính giữa màn hình, không thanh điều hướng. */
export function AuthLayout() {
  return (
    <div className="flex min-h-dvh items-center justify-center p-4">
      <m.div
        variants={RISE_IN}
        initial="hidden"
        animate="visible"
        transition={MOTION_SPRING}
        className="w-full max-w-md"
      >
        <div className="mb-6 flex flex-col items-center justify-center gap-2">
          <div className="flex size-12 items-center justify-center rounded-2xl bg-gradient-to-br from-[#FF8C42] to-[#FF5E62] text-white shadow-md shadow-orange-500/25">
            <GraduationCap size={26} aria-hidden />
          </div>
          <span className="text-xl font-bold tracking-tight text-slate-900">EduERP</span>
          <span className="text-xs font-medium text-slate-500">Hệ thống quản lý đào tạo trực tuyến</span>
        </div>
        <GlassPanel animate={false} className="glass-sheen p-7 shadow-lg">
          <Outlet />
        </GlassPanel>
        <p className="mt-6 text-center text-xs text-slate-400">
          Hệ thống quản lý đào tạo
        </p>
      </m.div>
    </div>
  );
}
