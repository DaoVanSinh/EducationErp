import type { AccountSummary } from "@/entities/account";
import { useRbacCatalog } from "@/entities/rbac-catalog";
import { useChangeAccountRole } from "@/modules/rbac/api/use-rbac-mutations";
import { isStaffRole } from "@/modules/rbac/model/account-rules";
import { changeRoleFormSchema } from "@/modules/rbac/model/rbac-forms";
import { useZodForm } from "@/shared/lib/use-zod-form";

/**
 * Đổi giữa các vai trò nhân viên. Vào hoặc ra khỏi Giáo viên, Học viên phải đóng tài khoản cũ rồi tạo mới, vai trò có
 * năng lực giảng dạy hay học tập tự tạo cũng bị backend chặn - lỗi đó hiện nguyên văn trong hộp thoại.
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
    roles: roles.filter((role) => role.code !== account.roleCode && isStaffRole(role.code)),
  };
}
