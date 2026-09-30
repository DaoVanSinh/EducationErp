import { useRbacCatalog } from "@/entities/rbac-catalog";
import { useCreateRole } from "@/modules/rbac/api/use-rbac-mutations";
import { createRoleFormSchema } from "@/modules/rbac/model/rbac-forms";
import { useZodForm } from "@/shared/lib/use-zod-form";
import { CheckableRow } from "@/shared/ui/checkable-row";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { FormField } from "@/shared/ui/form-field";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassInput } from "@/shared/ui/glass-input";
import { GlassModal } from "@/shared/ui/glass-modal";

export interface CreateRoleDialogProps {
  readonly open: boolean;
  readonly onClose: () => void;
}

export function CreateRoleDialog({ open, onClose }: CreateRoleDialogProps) {
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

  const selected = form.values.permissionGroupIds;
  const toggleGroup = (groupId: string) => {
    form.setValue(
      "permissionGroupIds",
      selected.includes(groupId) ? selected.filter((id) => id !== groupId) : [...selected, groupId],
    );
  };

  return (
    <GlassModal
      open={open}
      onClose={onClose}
      title="Tạo vai trò"
      description="Vai trò tạo từ giao diện không phải vai trò hệ thống, nên vẫn sửa được về sau."
    >
      <form id="create-role-form" onSubmit={form.handleSubmit} className="flex flex-col gap-4" noValidate>
        {form.submitError ? <ErrorNotice error={form.submitError} /> : null}

        <FormField
          label="Mã vai trò"
          htmlFor="role-code"
          hint="Chữ in hoa, số và dấu gạch dưới. Ví dụ: BRANCH_MANAGER."
          error={form.fieldErrors.code}
        >
          <GlassInput
            id="role-code"
            autoFocus
            value={form.values.code}
            invalid={form.fieldErrors.code !== undefined}
            onChange={(event) => form.setValue("code", event.target.value.toUpperCase())}
          />
        </FormField>

        <FormField label="Tên vai trò" htmlFor="role-name" error={form.fieldErrors.name}>
          <GlassInput
            id="role-name"
            value={form.values.name}
            invalid={form.fieldErrors.name !== undefined}
            onChange={(event) => form.setValue("name", event.target.value)}
          />
        </FormField>

        <FormField
          label="Nhóm quyền"
          htmlFor="role-permission-groups"
          hint="Có thể để trống rồi gán sau, nhưng khi đó vai trò chưa cho phép làm gì."
          error={form.fieldErrors.permissionGroupIds}
        >
          <div id="role-permission-groups" className="flex max-h-56 flex-col gap-2 overflow-y-auto">
            {(catalog.data?.permissionGroups ?? []).map((group) => (
              <CheckableRow
                key={group.id}
                label={group.name}
                checked={selected.includes(group.id)}
                onToggle={() => toggleGroup(group.id)}
              />
            ))}
          </div>
        </FormField>
      </form>

      <footer className="flex justify-end gap-2">
        <GlassButton variant="ghost" onClick={onClose}>
          Huỷ
        </GlassButton>
        <GlassButton type="submit" form="create-role-form" loading={form.isSubmitting}>
          Tạo vai trò
        </GlassButton>
      </footer>
    </GlassModal>
  );
}
