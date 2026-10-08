import { useChangePassword } from "@/modules/profile/api/use-profile-mutations";
import { changePasswordFormSchema } from "@/modules/profile/model/profile-forms";
import { useZodForm } from "@/shared/lib/use-zod-form";

export function useChangePasswordController() {
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

  return {
    values: form.values,
    fieldErrors: form.fieldErrors,
    submitError: form.submitError,
    isSubmitting: form.isSubmitting,
    isSuccess: changePassword.isSuccess && !form.isSubmitting,
    handleSubmit: form.handleSubmit,
    setCurrentPassword: (password: string) => form.setValue("currentPassword", password),
    setNewPassword: (password: string) => form.setValue("newPassword", password),
    setConfirmPassword: (password: string) => form.setValue("confirmPassword", password),
  };
}
