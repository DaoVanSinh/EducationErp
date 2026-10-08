import { useCreateRoleController } from "@/modules/rbac/hooks/use-create-role-controller";
import { CheckableRow } from "@/shared/ui/checkable-row";
import { EmptyState } from "@/shared/ui/empty-state";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { FormField } from "@/shared/ui/form-field";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassInput } from "@/shared/ui/glass-input";
import { GlassModal } from "@/shared/ui/glass-modal";
import { ShieldCheck } from "lucide-react";

export interface CreateRoleDialogProps {
  readonly open: boolean;
  readonly onClose: () => void;
}

export function CreateRoleDialog({ open, onClose }: CreateRoleDialogProps) {
  const {
    values,
    fieldErrors,
    submitError,
    isSubmitting,
    permissionGroups,
    selectedGroupIds,
    toggleGroup,
    handleSubmit,
    setCode,
    setName,
  } = useCreateRoleController({ onClose });

  return (
    <GlassModal
      open={open}
      onClose={onClose}
      title="Tạo vai trò"
      description="Định nghĩa vai trò mới và phân bổ các nhóm quyền tương ứng trong hệ thống."
      icon={<ShieldCheck size={22} aria-hidden />}
      className="max-w-xl"
    >
      <form id="create-role-form" onSubmit={handleSubmit} className="flex flex-col gap-4" noValidate>
        {submitError ? <ErrorNotice error={submitError} /> : null}

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <FormField
            label="Mã vai trò"
            htmlFor="role-code"
            hint="Chữ in hoa, số và dấu gạch dưới."
            error={fieldErrors.code}
          >
            <GlassInput
              id="role-code"
              autoFocus
              placeholder="VD: BRANCH_MANAGER"
              value={values.code}
              invalid={fieldErrors.code !== undefined}
              onChange={(event) => setCode(event.target.value)}
            />
          </FormField>

          <FormField label="Tên vai trò" htmlFor="role-name" error={fieldErrors.name}>
            <GlassInput
              id="role-name"
              placeholder="VD: Quản lý chi nhánh"
              value={values.name}
              invalid={fieldErrors.name !== undefined}
              onChange={(event) => setName(event.target.value)}
            />
          </FormField>
        </div>

        <FormField
          label={`Nhóm quyền gán kèm (${selectedGroupIds.length}/${permissionGroups.length})`}
          htmlFor="role-permission-groups"
          hint="Có thể để trống rồi gán sau nếu cần."
          error={fieldErrors.permissionGroupIds}
        >
          <div
            id="role-permission-groups"
            className="flex max-h-60 flex-col gap-2 overflow-y-auto rounded-2xl border border-slate-200/80 bg-slate-50/50 p-3 pr-1"
          >
            {permissionGroups.length === 0 ? (
              <EmptyState title="Chưa có nhóm quyền nào" />
            ) : (
              permissionGroups.map((group) => (
                <CheckableRow
                  key={group.id}
                  label={group.name}
                  description={`ID: ${group.id.slice(0, 8)}`}
                  checked={selectedGroupIds.includes(group.id)}
                  onToggle={() => toggleGroup(group.id)}
                />
              ))
            )}
          </div>
        </FormField>
      </form>

      <footer className="flex justify-end gap-2.5">
        <GlassButton variant="ghost" onClick={onClose}>
          Huỷ
        </GlassButton>
        <GlassButton type="submit" form="create-role-form" loading={isSubmitting}>
          Tạo vai trò
        </GlassButton>
      </footer>
    </GlassModal>
  );
}
