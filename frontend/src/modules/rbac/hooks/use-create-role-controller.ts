import { useRbacCatalog } from "@/entities/rbac-catalog";
import { useCreateRole } from "@/modules/rbac/api/use-rbac-mutations";
import { createRoleFormSchema } from "@/modules/rbac/model/rbac-forms";
import { useZodForm } from "@/shared/lib/use-zod-form";

export function useCreateRoleController({ onClose }: { readonly onClose: () => void }) {
  const catalog = useRbacCatalog();
  const createRole = useCreateRole();

  const form = useZodForm({
    schema: createRoleFormSchema,
    initialValues: { code: "", name: "", permissionGroupIds: [] },
    onSubmit: async (values) => {
      await createRole.mutateAsync(values);
      form.reset();
      onClose();
    },
  });

  const selected = form.values.permissionGroupIds as readonly string[];
  const toggleGroup = (groupId: string) => {
    form.setValue(
      "permissionGroupIds",
      selected.includes(groupId)
        ? selected.filter((id: string) => id !== groupId)
        : [...selected, groupId],
    );
  };

  return {
    values: form.values,
    fieldErrors: form.fieldErrors,
    submitError: form.submitError,
    isSubmitting: form.isSubmitting,
    permissionGroups: catalog.data?.permissionGroups ?? [],
    selectedGroupIds: selected,
    toggleGroup,
    handleSubmit: form.handleSubmit,
    setCode: (code: string) => form.setValue("code", code.toUpperCase()),
    setName: (name: string) => form.setValue("name", name),
  };
}
