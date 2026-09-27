import { useForgotPassword } from "@/modules/auth/api/use-auth-mutations";
import { forgotPasswordFormSchema } from "@/modules/auth/model/auth-forms";
import { useZodForm } from "@/shared/lib/use-zod-form";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { FormField } from "@/shared/ui/form-field";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassInput } from "@/shared/ui/glass-input";
import { MailCheck, Send } from "lucide-react";

export function ForgotPasswordForm() {
  const forgotPassword = useForgotPassword();
  const form = useZodForm({
    schema: forgotPasswordFormSchema,
    initialValues: { email: "" },
    onSubmit: (values) => forgotPassword.mutateAsync(values.email),
  });

  // Không nói email có tồn tại hay không: đó là cách một trang đăng nhập bị dùng để dò danh sách người dùng.
  if (forgotPassword.isSuccess) {
    return (
      <div className="flex flex-col items-center gap-3 text-center">
        <MailCheck size={32} className="text-aqua-300" aria-hidden />
        <p className="text-sm text-mist-200">
          Nếu email vừa nhập có tài khoản, liên kết đặt lại mật khẩu đã được gửi tới hộp thư đó.
        </p>
        <p className="text-xs text-mist-500">Liên kết có hiệu lực trong 30 phút.</p>
      </div>
    );
  }

  return (
    <form onSubmit={form.handleSubmit} className="flex flex-col gap-5" noValidate>
      {form.submitError ? <ErrorNotice error={form.submitError} /> : null}

      <FormField
        label="Email"
        htmlFor="forgot-email"
        hint="Nhập email đã đăng ký để nhận liên kết đặt lại mật khẩu."
        error={form.fieldErrors.email}
      >
        <GlassInput
          id="forgot-email"
          type="email"
          autoComplete="username"
          autoFocus
          value={form.values.email}
          invalid={form.fieldErrors.email !== undefined}
          onChange={(event) => form.setValue("email", event.target.value)}
        />
      </FormField>

      <GlassButton type="submit" loading={form.isSubmitting} icon={<Send size={16} aria-hidden />}>
        Gửi liên kết
      </GlassButton>
    </form>
  );
}
