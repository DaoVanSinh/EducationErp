import { PasswordInput } from "@/shared/ui/password-input";
import { useChangePassword } from "@/modules/profile/api/use-profile-mutations";
import { changePasswordFormSchema } from "@/modules/profile/model/profile-forms";
import { useZodForm } from "@/shared/lib/use-zod-form";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { FormField } from "@/shared/ui/form-field";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassPanel } from "@/shared/ui/glass-panel";
import { KeyRound } from "lucide-react";

export function ChangePasswordForm() {
  const changePassword = useChangePassword();
  const form = useZodForm({
    schema: changePasswordFormSchema,
    initialValues: { currentPassword: "", newPassword: "", confirmPassword: "" },
    onSubmit: async (values) => {
      await changePassword.mutateAsync({
        currentPassword: values.currentPassword,
        newPassword: values.newPassword,
      });
      form.reset();
    },
  });

  return (
    <GlassPanel>
      <form onSubmit={form.handleSubmit} className="flex flex-col gap-5" noValidate>
        <div>
          <h2 className="text-sm font-semibold tracking-wide text-mist-300 uppercase">Đổi mật khẩu</h2>
          <p className="mt-1 text-xs text-mist-500">
            Sau khi đổi, các thiết bị khác đang đăng nhập bằng tài khoản này sẽ bị đăng xuất.
          </p>
        </div>

        {form.submitError ? <ErrorNotice error={form.submitError} /> : null}
        {changePassword.isSuccess && !form.isSubmitting ? (
          <p className="rounded-2xl border border-aqua-400/30 bg-aqua-500/10 px-4 py-3 text-sm text-aqua-300">
            Đã đổi mật khẩu.
          </p>
        ) : null}

        <FormField
          label="Mật khẩu hiện tại"
          htmlFor="current-password"
          error={form.fieldErrors.currentPassword}
        >
          <PasswordInput
            id="current-password"
            autoComplete="current-password"
            value={form.values.currentPassword}
            invalid={form.fieldErrors.currentPassword !== undefined}
            onChange={(event) => form.setValue("currentPassword", event.target.value)}
          />
        </FormField>

        <FormField
          label="Mật khẩu mới"
          htmlFor="new-password"
          hint="Tối thiểu 8 ký tự."
          error={form.fieldErrors.newPassword}
        >
          <PasswordInput
            id="new-password"
            autoComplete="new-password"
            value={form.values.newPassword}
            invalid={form.fieldErrors.newPassword !== undefined}
            onChange={(event) => form.setValue("newPassword", event.target.value)}
          />
        </FormField>

        <FormField
          label="Nhập lại mật khẩu mới"
          htmlFor="confirm-password"
          error={form.fieldErrors.confirmPassword}
        >
          <PasswordInput
            id="confirm-password"
            autoComplete="new-password"
            value={form.values.confirmPassword}
            invalid={form.fieldErrors.confirmPassword !== undefined}
            onChange={(event) => form.setValue("confirmPassword", event.target.value)}
          />
        </FormField>

        <GlassButton
          type="submit"
          variant="secondary"
          loading={form.isSubmitting}
          icon={<KeyRound size={16} aria-hidden />}
        >
          Đổi mật khẩu
        </GlassButton>
      </form>
    </GlassPanel>
  );
}
