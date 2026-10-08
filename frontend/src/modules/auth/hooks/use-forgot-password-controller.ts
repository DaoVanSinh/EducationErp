import { useForgotPassword } from "@/modules/auth/api/use-auth-mutations";
import { forgotPasswordFormSchema } from "@/modules/auth/model/auth-forms";
import { useZodForm } from "@/shared/lib/use-zod-form";

export function useForgotPasswordController() {
  const forgotPassword = useForgotPassword();
  const form = useZodForm({
    schema: forgotPasswordFormSchema,
    initialValues: { email: "" },
    onSubmit: (values) => forgotPassword.mutateAsync(values.email),
  });

  return {
    values: form.values,
    fieldErrors: form.fieldErrors,
    submitError: form.submitError,
    isSubmitting: form.isSubmitting,
    isSuccess: forgotPassword.isSuccess,
    handleSubmit: form.handleSubmit,
    setEmail: (email: string) => form.setValue("email", email),
  };
}
