import { useLogin } from "@/modules/auth/api/use-auth-mutations";
import { loginFormSchema } from "@/modules/auth/model/auth-forms";
import { PasswordInput } from "@/shared/ui/password-input";
import { APP_ROUTE } from "@/shared/constants/app-routes";
import { useZodForm } from "@/shared/lib/use-zod-form";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { FormField } from "@/shared/ui/form-field";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassInput } from "@/shared/ui/glass-input";
import { LogIn } from "lucide-react";
import { Link } from "react-router-dom";

export function LoginForm({ redirectTo }: { readonly redirectTo: string }) {
  const login = useLogin(redirectTo);
  const form = useZodForm({
    schema: loginFormSchema,
    initialValues: { email: "", password: "" },
    onSubmit: (values) => login.mutateAsync(values),
  });

  return (
    <form onSubmit={form.handleSubmit} className="flex flex-col gap-5" noValidate>
      {form.submitError ? <ErrorNotice error={form.submitError} /> : null}

      <FormField label="Email" htmlFor="login-email" error={form.fieldErrors.email}>
        <GlassInput
          id="login-email"
          type="email"
          autoComplete="username"
          autoFocus
          placeholder="ten@eduerp.local"
          value={form.values.email}
          invalid={form.fieldErrors.email !== undefined}
          onChange={(event) => form.setValue("email", event.target.value)}
        />
      </FormField>

      <FormField label="Mật khẩu" htmlFor="login-password" error={form.fieldErrors.password}>
        <PasswordInput
          id="login-password"
          autoComplete="current-password"
          placeholder="••••••••"
          value={form.values.password}
          invalid={form.fieldErrors.password !== undefined}
          onChange={(event) => form.setValue("password", event.target.value)}
        />
      </FormField>

      <GlassButton type="submit" loading={form.isSubmitting} icon={<LogIn size={16} aria-hidden />}>
        Đăng nhập
      </GlassButton>

      <Link
        to={APP_ROUTE.forgotPassword}
        className="text-center text-sm text-mist-400 transition-colors hover:text-aqua-300"
      >
        Quên mật khẩu?
      </Link>
    </form>
  );
}
