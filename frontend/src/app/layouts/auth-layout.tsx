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
        <div className="mb-6 flex items-center justify-center gap-2 text-mist-200">
          <GraduationCap size={22} className="text-aqua-300" aria-hidden />
          <span className="text-lg font-semibold tracking-tight">EduERP</span>
        </div>
        <GlassPanel animate={false} className="glass-sheen p-7">
          <Outlet />
        </GlassPanel>
        <p className="mt-6 text-center text-xs text-mist-600">
          Hệ thống quản lý đào tạo · Phân hệ quản trị người dùng
        </p>
      </m.div>
    </div>
  );
}
