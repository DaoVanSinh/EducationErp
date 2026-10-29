import type { RoleOption } from "@/entities/rbac-catalog";
import { useEditRoleController } from "@/modules/rbac/hooks/use-edit-role-controller";
import { CheckableRow } from "@/shared/ui/checkable-row";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { FormField } from "@/shared/ui/form-field";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassInput } from "@/shared/ui/glass-input";
import { GlassModal } from "@/shared/ui/glass-modal";
import { ShieldCheck } from "lucide-react";

export interface EditRoleDialogProps {
  readonly role: RoleOption;
  readonly open: boolean;
  readonly onClose: () => void;
}

export function EditRoleDialog({ role, open, onClose }: EditRoleDialogProps) {
  const controller = useEditRoleController({ role, onClose });

  return (
    <GlassModal
      open={open}
      onClose={onClose}
      title={`Sửa vai trò ${role.code}`}
      description={`${role.accountCount} tài khoản đang giữ vai trò này sẽ nhận quyền mới ngay.`}
      icon={<ShieldCheck size={22} aria-hidden />}
      className="max-w-xl"
    >
      <form id="edit-role-form" onSubmit={controller.handleSubmit} className="flex flex-col gap-4" noValidate>
        {controller.submitError ? <ErrorNotice error={controller.submitError} /> : null}

        <FormField label="Tên vai trò" htmlFor="edit-role-name" error={controller.fieldErrors.name}>
          <GlassInput
            id="edit-role-name"
            value={controller.values.name}
            invalid={controller.fieldErrors.name !== undefined}
            onChange={(event) => controller.setName(event.target.value)}
          />
        </FormField>

        <FormField
          label={`Nhóm quyền của vai trò (${controller.selectedGroupIds.length}/${controller.permissionGroups.length})`}
          htmlFor="edit-role-permission-groups"
          hint="Quyền hiệu lực = hợp các nhóm quyền đã chọn; cùng một quyền thì phạm vi rộng nhất thắng."
        >
          <div
            id="edit-role-permission-groups"
            className="flex max-h-72 flex-col gap-2 overflow-y-auto rounded-2xl border border-slate-200/80 bg-slate-50/50 p-3 pr-1"
          >
            {controller.permissionGroups.map((group) => (
              <CheckableRow
                key={group.id}
                label={group.name}
                description={group.description ?? `${group.items.length} quyền`}
                checked={controller.selectedGroupIds.includes(group.id)}
                onToggle={() => controller.toggleGroup(group.id)}
              />
            ))}
          </div>
        </FormField>
      </form>

      <footer className="flex justify-end gap-2.5">
        <GlassButton variant="ghost" onClick={onClose}>
          Huỷ
        </GlassButton>
        <GlassButton type="submit" form="edit-role-form" loading={controller.isSubmitting}>
          Lưu vai trò
        </GlassButton>
      </footer>
    </GlassModal>
  );
}
