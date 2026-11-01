import type { useLoginFormController } from "@/modules/auth/hooks/use-login-form-controller";
import { APP_ROUTE } from "@/shared/constants/app-routes";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { Spinner } from "@/shared/ui/spinner";
import { Eye, EyeOff } from "lucide-react";
import { useState } from "react";
import { Link } from "react-router-dom";

export interface LoginFormProps {
  readonly controller: ReturnType<typeof useLoginFormController>;
}

export function LoginForm({ controller }: LoginFormProps) {
  const { values, fieldErrors, submitError, isSubmitting, handleSubmit, setEmail, setPassword } =
    controller;
  const [showPassword, setShowPassword] = useState(false);

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-5" noValidate>
      {submitError ? <ErrorNotice error={submitError} /> : null}

      <div className="flex flex-col gap-1.5">
        <label htmlFor="login-email" className="text-[11px] font-medium text-[#94a3b8]">
          Email tài khoản
        </label>
        <div className="relative">
          <input
            id="login-email"
            type="email"
            autoComplete="username"
            autoFocus
            placeholder="name@dsacademy.edu.vn"
            value={values.email}
            aria-invalid={fieldErrors.email !== undefined}
            onChange={(event) => setEmail(event.target.value)}
            className="h-11 w-full rounded-[4px] border border-slate-200/90 bg-white px-3.5 pr-8 text-xs font-medium text-[#334155] shadow-[0_2px_10px_rgba(15,23,42,0.03)] transition-colors placeholder:text-slate-300 focus:border-[#ff8c42] focus:outline-none"
          />
          <span
            aria-hidden
            className="pointer-events-none absolute top-1/2 right-3.5 -translate-y-1/2 text-xs font-bold text-[#ff5e62]"
          >
            *
          </span>
        </div>
        {fieldErrors.email ? (
          <span role="alert" className="text-[11px] text-rose-600">
            {fieldErrors.email}
          </span>
        ) : null}
      </div>

      <div className="flex flex-col gap-1.5">
        <label htmlFor="login-password" className="text-[11px] font-medium text-[#94a3b8]">
          Mật khẩu
        </label>
        <div className="relative">
          <input
            id="login-password"
            type={showPassword ? "text" : "password"}
            autoComplete="current-password"
            placeholder="••••••••"
            value={values.password}
            aria-invalid={fieldErrors.password !== undefined}
            onChange={(event) => setPassword(event.target.value)}
            className="h-11 w-full rounded-[4px] border border-slate-200/90 bg-white px-3.5 pr-10 text-xs font-medium text-[#334155] shadow-[0_2px_10px_rgba(15,23,42,0.03)] transition-colors placeholder:text-slate-300 focus:border-[#ff8c42] focus:outline-none"
          />
          <button
            type="button"
            onClick={() => setShowPassword((prev) => !prev)}
            aria-label={showPassword ? "Ẩn mật khẩu" : "Hiện mật khẩu"}
            className="absolute inset-y-0 right-0 flex w-9 cursor-pointer items-center justify-center text-slate-400 hover:text-[#ea580c]"
          >
            {showPassword ? <EyeOff size={14} /> : <Eye size={14} />}
          </button>
        </div>
        {fieldErrors.password ? (
          <span role="alert" className="text-[11px] text-rose-600">
            {fieldErrors.password}
          </span>
        ) : null}
      </div>

      <div className="mt-2 flex items-center justify-between gap-4">
        <button
          type="submit"
          disabled={isSubmitting}
          className="inline-flex h-10 min-w-[124px] cursor-pointer items-center justify-center gap-2 rounded-full bg-gradient-to-r from-[#ff9f5a] to-[#ff7828] px-7 text-xs font-bold text-white shadow-[0_8px_20px_-4px_rgba(255,140,66,0.55)] transition-all hover:-translate-y-0.5 hover:opacity-95 disabled:cursor-not-allowed disabled:opacity-60"
        >
          {isSubmitting ? <Spinner size={14} /> : null}
          <span>Đăng nhập</span>
        </button>

        <Link
          to={APP_ROUTE.forgotPassword}
          className="text-[11.5px] font-medium text-[#64748b] transition-colors hover:text-[#ea580c]"
        >
          Quên mật khẩu? <strong className="font-bold text-[#334155]">Khôi phục</strong>
        </Link>
      </div>
    </form>
  );
}
