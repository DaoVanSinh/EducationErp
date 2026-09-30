import { useLogin } from "@/modules/auth/api/use-auth-mutations";
import { loginFormSchema } from "@/modules/auth/model/auth-forms";
import { useZodForm } from "@/shared/lib/use-zod-form";

export function useLoginFormController(redirectTo: string) {
  const login = useLogin(redirectTo);
  const form = useZodForm({
    schema: loginFormSchema,
    initialValues: { email: "", password: "" },
    onSubmit: (values) => login.mutateAsync(values),
  });

  return {
    values: form.values,
    fieldErrors: form.fieldErrors,
    submitError: form.submitError,
    isSubmitting: form.isSubmitting,
    handleSubmit: form.handleSubmit,
    setEmail: (email: string) => form.setValue("email", email),
    setPassword: (password: string) => form.setValue("password", password),
  };
}
