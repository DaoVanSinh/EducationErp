import { useRbacCatalog } from "@/entities/rbac-catalog";
import { useCreateAccount } from "@/modules/rbac/api/use-rbac-mutations";
import { createAccountFormSchema } from "@/modules/rbac/model/rbac-forms";
import { useZodForm } from "@/shared/lib/use-zod-form";

export function useCreateAccountController({ onClose }: { readonly onClose: () => void }) {
  const catalog = useRbacCatalog();
  const createAccount = useCreateAccount();

  const form = useZodForm({
    schema: createAccountFormSchema,
    initialValues: { email: "", fullName: "", homeBranchId: "", roleId: "" },
    onSubmit: async (values) => {
      await createAccount.mutateAsync({
        email: values.email,
        fullName: values.fullName,
        homeBranchId: values.homeBranchId.length === 0 ? null : values.homeBranchId,
        roleId: values.roleId,
      });
      form.reset();
      onClose();
    },
  });

  return {
    values: form.values,
    fieldErrors: form.fieldErrors,
    submitError: form.submitError,
    isSubmitting: form.isSubmitting,
    branches: catalog.data?.branches ?? [],
    roles: catalog.data?.roles ?? [],
    handleSubmit: form.handleSubmit,
    setValue: form.setValue,
  };
}
