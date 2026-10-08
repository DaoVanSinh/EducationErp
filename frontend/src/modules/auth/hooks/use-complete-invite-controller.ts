import { useCompleteInvite } from "@/modules/auth/api/use-auth-mutations";
import { completeInviteFormSchema } from "@/modules/auth/model/auth-forms";
import { useZodForm } from "@/shared/lib/use-zod-form";

export function useCompleteInviteController({
  email,
  temporaryPassword,
  redirectTo,
}: {
  readonly email: string;
  readonly temporaryPassword: string;
  readonly redirectTo: string;
}) {
  const completeInvite = useCompleteInvite(redirectTo);

  const form = useZodForm({
    schema: completeInviteFormSchema,
    initialValues: { newPassword: "", confirmPassword: "" },
    onSubmit: (values) =>
      completeInvite.mutateAsync({ email, currentPassword: temporaryPassword, newPassword: values.newPassword }),
  });

  return {
    values: form.values,
    fieldErrors: form.fieldErrors,
    submitError: form.submitError,
    isSubmitting: form.isSubmitting,
    handleSubmit: form.handleSubmit,
    setNewPassword: (value: string) => form.setValue("newPassword", value),
    setConfirmPassword: (value: string) => form.setValue("confirmPassword", value),
  };
}
