import { APP_ROUTE } from "@/shared/constants/app-routes";
import { MOTION_SPRING, RISE_IN } from "@/shared/lib/motion";
import { BrandLogoIcon } from "@/shared/ui/brand-logo";
import { m } from "framer-motion";
import { ArrowLeft } from "lucide-react";
import { Link, Outlet } from "react-router-dom";

/**
 * Bố cục trang Đăng nhập DS Academy:
 * - Nền phải: Ảnh chân dung người Việt cầm MacBook trong không gian học viện/văn phòng hiện đại.
 * - Nền trái: Mảng chéo (diagonal wedge) màu Cam Pastel nổi bật.
 * - Khung thẻ trắng phẳng sắc nét đặt lệch trái đè lên đường cắt chéo, có bóng đổ khối lệch góc trái dưới.
 */
export function AuthLayout() {
  return (
    <div className="relative flex min-h-dvh w-full items-center overflow-hidden bg-[#f4f5f7]">
      {/* Lớp 1: Ảnh nền toàn khung - Người Việt cầm MacBook bên phải */}
      <div className="absolute inset-0 z-0">
        <img
          src="/images/viet_macbook_login.png"
          alt="Không gian làm việc và học tập hiện đại DS Academy"
          className="h-full w-full object-cover object-[72%_center] sm:object-[68%_center] lg:object-center"
        />
        <div
          aria-hidden
          className="absolute inset-0 bg-gradient-to-r from-white/35 via-transparent to-transparent lg:from-transparent"
        />
      </div>

      {/* Lớp 2: Mảng cắt chéo màu Cam Pastel bên trái (Diagonal Pastel Orange Wedge) */}
      <div
        aria-hidden
        className="pointer-events-none absolute inset-y-0 left-0 z-10 w-full bg-gradient-to-b from-[#ffa96b] via-[#ff8c42] to-[#f97316]"
        style={{
          clipPath: "polygon(0 0, 24% 0, 50% 100%, 0% 100%)",
        }}
      />

      {/* Nút quay về Trang chủ nhỏ gọn góc trên trái */}
      <div className="fixed top-5 left-5 z-30 sm:top-6 sm:left-8">
        <Link
          to={APP_ROUTE.landing}
          className="inline-flex items-center gap-1.5 rounded-full bg-white/90 px-3.5 py-1.5 text-xs font-bold text-[#ea580c] shadow-sm backdrop-blur-md transition-transform hover:-translate-x-0.5 hover:bg-white"
        >
          <ArrowLeft size={13} aria-hidden />
          <span>Trang chủ</span>
        </Link>
      </div>

      {/* Lớp 3: Khung thẻ trắng Đăng nhập đặt bên trái đè lên đường chéo Cam Pastel */}
      <div className="relative z-20 mx-auto flex w-full max-w-[1380px] items-center px-4 py-14 sm:px-10 lg:px-16">
        <m.div
          variants={RISE_IN}
          initial="hidden"
          animate="visible"
          transition={MOTION_SPRING}
          className="w-full max-w-[430px] bg-white px-8 py-10 shadow-[-16px_16px_0px_rgba(234,88,12,0.22),0_25px_60px_-12px_rgba(15,23,42,0.22)] sm:px-11 sm:py-12 lg:ml-6"
        >
          <Outlet />
        </m.div>
      </div>

      {/* Lớp 4: Logo thương hiệu DS Academy góc phải dưới */}
      <div className="pointer-events-none fixed right-6 bottom-6 z-20 hidden items-center gap-2.5 sm:flex lg:right-10 lg:bottom-8">
        <BrandLogoIcon size="sm" />
        <div className="relative flex items-center py-1.5 pl-2.5">
          <span
            aria-hidden
            className="absolute inset-y-0 left-0 w-7 border-2 border-r-0 border-[#ff8c42]"
          />
          <span className="relative z-10 text-base font-black tracking-tight text-[#1e293b] drop-shadow-[0_1px_8px_rgba(255,255,255,0.9)]">
            DS <span className="text-xs font-bold text-[#ea580c] lowercase">academy.</span>
          </span>
        </div>
      </div>
    </div>
  );
}
