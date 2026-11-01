import { ForgotPasswordForm } from "@/modules/auth/ui/forgot-password-form";
import { APP_ROUTE } from "@/shared/constants/app-routes";
import { ArrowLeft } from "lucide-react";
import { Link } from "react-router-dom";

export function ForgotPasswordPage() {
  return (
    <div className="flex flex-col gap-6">
      <div className="text-center">
        <h1 className="text-2xl font-black tracking-tight text-[#1e293b]">Quên mật khẩu</h1>
        <p className="mt-1.5 text-[13.5px] leading-relaxed text-[#64748b]">
          Nhập địa chỉ email của bạn để nhận liên kết đặt lại mật khẩu.
        </p>
      </div>
      <ForgotPasswordForm />
      <Link
        to={APP_ROUTE.login}
        className="inline-flex items-center justify-center gap-2 border-t border-[#ffedd5] pt-4 text-sm font-bold text-[#ea580c] transition-colors hover:text-[#c2410c]"
      >
        <ArrowLeft size={14} aria-hidden />
        Về trang đăng nhập
      </Link>
    </div>
  );
}
