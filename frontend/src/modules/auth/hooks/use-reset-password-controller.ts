import { useResetPassword } from "@/modules/auth/api/use-auth-mutations";
import { resetPasswordFormSchema } from "@/modules/auth/model/auth-forms";
import { useZodForm } from "@/shared/lib/use-zod-form";

export function useResetPasswordController(token: string) {
  const resetPassword = useResetPassword();
  const form = useZodForm({
    schema: resetPasswordFormSchema,
    initialValues: { token, newPassword: "", confirmPassword: "" },
    onSubmit: (values) =>
      resetPassword.mutateAsync({ token: values.token, newPassword: values.newPassword }),
  });

  return {
    values: form.values,
    fieldErrors: form.fieldErrors,
    submitError: form.submitError,
    isSubmitting: form.isSubmitting,
    isSuccess: resetPassword.isSuccess,
    handleSubmit: form.handleSubmit,
    setNewPassword: (password: string) => form.setValue("newPassword", password),
    setConfirmPassword: (password: string) => form.setValue("confirmPassword", password),
  };
}
