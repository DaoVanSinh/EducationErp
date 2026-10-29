import { useRbacCatalog, type RoleOption } from "@/entities/rbac-catalog";
import { useUpdateRole } from "@/modules/rbac/api/use-rbac-mutations";
import { updateRoleFormSchema } from "@/modules/rbac/model/rbac-forms";
import { useZodForm } from "@/shared/lib/use-zod-form";

/**
 * Sửa tên và bộ nhóm quyền của một vai trò (spec mục 5: vai trò hệ thống "không xoá được nhưng sửa được
 * quyền"). Mọi người đang giữ vai trò nhận quyền mới ngay ở lần thao tác kế tiếp.
 */
export function useEditRoleController({ role, onClose }: { readonly role: RoleOption; readonly onClose: () => void }) {
  const catalog = useRbacCatalog();
  const updateRole = useUpdateRole(role.id);

  const form = useZodForm({
    schema: updateRoleFormSchema,
    initialValues: { name: role.name, permissionGroupIds: [...role.permissionGroupIds] },
    onSubmit: async (values) => {
      await updateRole.mutateAsync(values);
      onClose();
    },
  });

  const selected = form.values.permissionGroupIds as readonly string[];
  const toggleGroup = (groupId: string) => {
    form.setValue(
      "permissionGroupIds",
      selected.includes(groupId) ? selected.filter((id) => id !== groupId) : [...selected, groupId],
    );
  };

  return {
    values: form.values,
    fieldErrors: form.fieldErrors,
    submitError: form.submitError,
    isSubmitting: form.isSubmitting,
    handleSubmit: form.handleSubmit,
    setName: (name: string) => form.setValue("name", name),
    permissionGroups: catalog.data?.permissionGroups ?? [],
    selectedGroupIds: selected,
    toggleGroup,
  };
}
