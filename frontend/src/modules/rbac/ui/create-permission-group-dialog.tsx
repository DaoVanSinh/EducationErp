import { permissionLabel, useRbacCatalog, type PermissionOption } from "@/entities/rbac-catalog";
import { useCreatePermissionGroup } from "@/modules/rbac/api/use-rbac-mutations";
import { createPermissionGroupFormSchema } from "@/modules/rbac/model/rbac-forms";
import { CheckableRow } from "@/modules/rbac/ui/checkable-row";
import {
  PERMISSION_SCOPE,
  PERMISSION_SCOPE_LABEL,
  type PermissionScope,
} from "@/shared/constants/permissions";
import { useZodForm } from "@/shared/lib/use-zod-form";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { FormField } from "@/shared/ui/form-field";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassInput } from "@/shared/ui/glass-input";
import { GlassModal } from "@/shared/ui/glass-modal";
import { GlassSelect } from "@/shared/ui/glass-select";

const SCOPE_OPTIONS: readonly PermissionScope[] = [
  PERMISSION_SCOPE.personal,
  PERMISSION_SCOPE.branch,
  PERMISSION_SCOPE.organization,
];

export interface CreatePermissionGroupDialogProps {
  readonly open: boolean;
  readonly onClose: () => void;
}

/**
 * Mỗi quyền được chọn phải kèm một scope: cùng một quyền ACCOUNT:UPDATE ở scope PERSONAL là "tự sửa
 * hồ sơ mình", còn ở ORGANIZATION là "sửa được mọi người". Vì vậy không có ô chọn nào không có scope.
 */
export function CreatePermissionGroupDialog({ open, onClose }: CreatePermissionGroupDialogProps) {
  const catalog = useRbacCatalog();
  const createPermissionGroup = useCreatePermissionGroup();

  const form = useZodForm({
    schema: createPermissionGroupFormSchema,
    initialValues: { name: "", description: "", items: [] },
    onSubmit: async (values) => {
      await createPermissionGroup.mutateAsync(values);
      form.reset();
      onClose();
    },
  });

  const items = form.values.items;
  const scopeOf = (permissionId: string): PermissionScope | undefined =>
    items.find((item) => item.permissionId === permissionId)?.scope;

  const togglePermission = (permission: PermissionOption) => {
    form.setValue(
      "items",
      scopeOf(permission.id) === undefined
        ? [...items, { permissionId: permission.id, scope: PERMISSION_SCOPE.branch }]
        : items.filter((item) => item.permissionId !== permission.id),
    );
  };

  const changeScope = (permissionId: string, scope: PermissionScope) => {
    form.setValue(
      "items",
      items.map((item) => (item.permissionId === permissionId ? { ...item, scope } : item)),
    );
  };

  return (
    <GlassModal
      open={open}
      onClose={onClose}
      title="Tạo nhóm quyền"
      description="Nhóm quyền là đơn vị được gán cho vai trò và nhóm người dùng."
      className="max-w-2xl"
    >
      <form
        id="create-permission-group-form"
        onSubmit={form.handleSubmit}
        className="flex flex-col gap-4"
        noValidate
      >
        {form.submitError ? <ErrorNotice error={form.submitError} /> : null}

        <FormField label="Tên nhóm quyền" htmlFor="permission-group-name" error={form.fieldErrors.name}>
          <GlassInput
            id="permission-group-name"
            autoFocus
            value={form.values.name}
            invalid={form.fieldErrors.name !== undefined}
            onChange={(event) => form.setValue("name", event.target.value)}
          />
        </FormField>

        <FormField
          label="Mô tả"
          htmlFor="permission-group-description"
          hint="Không bắt buộc. Một câu để người sau hiểu nhóm này dành cho ai."
          error={form.fieldErrors.description}
        >
          <GlassInput
            id="permission-group-description"
            value={form.values.description}
            onChange={(event) => form.setValue("description", event.target.value)}
          />
        </FormField>

        <FormField
          label={`Quyền trong nhóm (${items.length})`}
          htmlFor="permission-group-items"
          error={form.fieldErrors.items}
        >
          <div id="permission-group-items" className="flex max-h-72 flex-col gap-2 overflow-y-auto pr-1">
            {(catalog.data?.permissions ?? []).map((permission) => {
              const scope = scopeOf(permission.id);
              return (
                <CheckableRow
                  key={permission.id}
                  label={permissionLabel(permission)}
                  description={`${permission.resource} · ${permission.action}`}
                  checked={scope !== undefined}
                  onToggle={() => togglePermission(permission)}
                  trailing={
                    scope === undefined ? null : (
                      <GlassSelect
                        aria-label={`Phạm vi của quyền ${permissionLabel(permission)}`}
                        value={scope}
                        className="h-9 w-40 shrink-0 text-xs"
                        onChange={(event) =>
                          changeScope(permission.id, event.target.value as PermissionScope)
                        }
                      >
                        {SCOPE_OPTIONS.map((option) => (
                          <option key={option} value={option}>
                            {PERMISSION_SCOPE_LABEL[option]}
                          </option>
                        ))}
                      </GlassSelect>
                    )
                  }
                />
              );
            })}
          </div>
        </FormField>
      </form>

      <footer className="flex justify-end gap-2">
        <GlassButton variant="ghost" onClick={onClose}>
          Huỷ
        </GlassButton>
        <GlassButton type="submit" form="create-permission-group-form" loading={form.isSubmitting}>
          Tạo nhóm quyền
        </GlassButton>
      </footer>
    </GlassModal>
  );
}
