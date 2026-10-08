import { useLogin } from "@/modules/auth/api/use-auth-mutations";
import { loginFormSchema } from "@/modules/auth/model/auth-forms";
import { useZodForm } from "@/shared/lib/use-zod-form";
import { useState } from "react";

export function useLoginFormController(redirectTo: string) {
  const login = useLogin(redirectTo);
  const [requiresPasswordChange, setRequiresPasswordChange] = useState(false);
  const form = useZodForm({
    schema: loginFormSchema,
    initialValues: { email: "", password: "" },
    onSubmit: async (values) => {
      const result = await login.mutateAsync(values);
      if (result.requiresPasswordChange) {
        setRequiresPasswordChange(true);
      }
    },
  });

  return {
    values: form.values,
    fieldErrors: form.fieldErrors,
    submitError: form.submitError,
    isSubmitting: form.isSubmitting,
    handleSubmit: form.handleSubmit,
    setEmail: (email: string) => form.setValue("email", email),
    setPassword: (password: string) => form.setValue("password", password),
    requiresPasswordChange,
    email: form.values.email,
    temporaryPassword: form.values.password,
  };
}
