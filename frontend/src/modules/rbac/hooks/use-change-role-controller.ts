import type { AccountSummary } from "@/entities/account";
import { useRbacCatalog } from "@/entities/rbac-catalog";
import { useChangeAccountRole } from "@/modules/rbac/api/use-rbac-mutations";
import { changeRoleFormSchema } from "@/modules/rbac/model/rbac-forms";
import { useZodForm } from "@/shared/lib/use-zod-form";

/**
 * Đổi vai trò một tài khoản đã có. Backend chặn khi đổi làm mất năng lực giáo viên của người còn dạy lớp mở,
 * hoặc mất năng lực học viên của người còn đang học - lỗi đó hiện nguyên văn trong hộp thoại.
 */
export function useChangeRoleController({
  account,
  onClose,
}: {
  readonly account: AccountSummary;
  readonly onClose: () => void;
}) {
  const catalog = useRbacCatalog();
  const changeRole = useChangeAccountRole(account.id);
  const roles = catalog.data?.roles ?? [];
  const currentRole = roles.find((role) => role.code === account.roleCode);

  const form = useZodForm({
    schema: changeRoleFormSchema,
    initialValues: { roleId: "" },
    onSubmit: async (values) => {
      await changeRole.mutateAsync(values.roleId);
      onClose();
    },
  });

  return {
    values: form.values,
    fieldErrors: form.fieldErrors,
    submitError: form.submitError,
    isSubmitting: form.isSubmitting,
    handleSubmit: form.handleSubmit,
    setRoleId: (roleId: string) => form.setValue("roleId", roleId),
    currentRoleName: currentRole?.name ?? account.roleCode,
    roles: roles.filter((role) => role.code !== account.roleCode),
  };
}
