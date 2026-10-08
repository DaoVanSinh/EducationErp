import type { useLoginFormController } from "@/modules/auth/hooks/use-login-form-controller";
import { APP_ROUTE } from "@/shared/constants/app-routes";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { FormField } from "@/shared/ui/form-field";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassInput } from "@/shared/ui/glass-input";
import { PasswordInput } from "@/shared/ui/password-input";
import { LogIn } from "lucide-react";
import { Link } from "react-router-dom";

export interface LoginFormProps {
  readonly controller: ReturnType<typeof useLoginFormController>;
}

export function LoginForm({ controller }: LoginFormProps) {
  const { values, fieldErrors, submitError, isSubmitting, handleSubmit, setEmail, setPassword } = controller;

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-5" noValidate>
      {submitError ? <ErrorNotice error={submitError} /> : null}

      <FormField label="Email" htmlFor="login-email" error={fieldErrors.email}>
        <GlassInput
          id="login-email"
          type="email"
          autoComplete="username"
          autoFocus
          placeholder="ten@eduerp.local"
          value={values.email}
          invalid={fieldErrors.email !== undefined}
          onChange={(event) => setEmail(event.target.value)}
        />
      </FormField>

      <FormField label="Mật khẩu" htmlFor="login-password" error={fieldErrors.password}>
        <PasswordInput
          id="login-password"
          autoComplete="current-password"
          placeholder="••••••••"
          value={values.password}
          invalid={fieldErrors.password !== undefined}
          onChange={(event) => setPassword(event.target.value)}
        />
      </FormField>

      <GlassButton type="submit" loading={isSubmitting} icon={<LogIn size={16} aria-hidden />}>
        Đăng nhập
      </GlassButton>

      <Link
        to={APP_ROUTE.forgotPassword}
        className="text-center text-sm font-medium text-slate-500 transition-colors hover:text-orange-600"
      >
        Quên mật khẩu?
      </Link>
    </form>
  );
}
