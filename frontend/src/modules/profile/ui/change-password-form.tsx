import { useChangePasswordController } from "@/modules/profile/hooks/use-change-password-controller";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { FormField } from "@/shared/ui/form-field";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassPanel } from "@/shared/ui/glass-panel";
import { PasswordInput } from "@/shared/ui/password-input";
import { KeyRound } from "lucide-react";

export function ChangePasswordForm() {
  const {
    values,
    fieldErrors,
    submitError,
    isSubmitting,
    isSuccess,
    handleSubmit,
    setCurrentPassword,
    setNewPassword,
    setConfirmPassword,
  } = useChangePasswordController();

  return (
    <GlassPanel>
      <form onSubmit={handleSubmit} className="flex flex-col gap-5" noValidate>
        <div>
          <h2 className="text-sm font-bold tracking-wide text-slate-800 uppercase">Đổi mật khẩu</h2>
          <p className="mt-1 text-xs text-slate-500">
            Sau khi đổi, các thiết bị khác đang đăng nhập bằng tài khoản này sẽ bị đăng xuất.
          </p>
        </div>

        {submitError ? <ErrorNotice error={submitError} /> : null}
        {isSuccess ? (
          <p className="rounded-2xl border border-emerald-300/80 bg-emerald-50 px-4 py-3 text-sm font-medium text-emerald-800">
            Đã đổi mật khẩu thành công.
          </p>
        ) : null}

        <FormField
          label="Mật khẩu hiện tại"
          htmlFor="current-password"
          error={fieldErrors.currentPassword}
        >
          <PasswordInput
            id="current-password"
            autoComplete="current-password"
            value={values.currentPassword}
            invalid={fieldErrors.currentPassword !== undefined}
            onChange={(event) => setCurrentPassword(event.target.value)}
          />
        </FormField>

        <FormField
          label="Mật khẩu mới"
          htmlFor="new-password"
          hint="Tối thiểu 8 ký tự."
          error={fieldErrors.newPassword}
        >
          <PasswordInput
            id="new-password"
            autoComplete="new-password"
            value={values.newPassword}
            invalid={fieldErrors.newPassword !== undefined}
            onChange={(event) => setNewPassword(event.target.value)}
          />
        </FormField>

        <FormField
          label="Nhập lại mật khẩu mới"
          htmlFor="confirm-password"
          error={fieldErrors.confirmPassword}
        >
          <PasswordInput
            id="confirm-password"
            autoComplete="new-password"
            value={values.confirmPassword}
            invalid={fieldErrors.confirmPassword !== undefined}
            onChange={(event) => setConfirmPassword(event.target.value)}
          />
        </FormField>

        <GlassButton
          type="submit"
          variant="secondary"
          loading={isSubmitting}
          icon={<KeyRound size={16} aria-hidden />}
        >
          Đổi mật khẩu
        </GlassButton>
      </form>
    </GlassPanel>
  );
}
