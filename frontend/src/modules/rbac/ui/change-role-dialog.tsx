import type { AccountSummary } from "@/entities/account";
import { useChangeRoleController } from "@/modules/rbac/hooks/use-change-role-controller";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { FormField } from "@/shared/ui/form-field";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassModal } from "@/shared/ui/glass-modal";
import { GlassSelect } from "@/shared/ui/glass-select";

export interface ChangeRoleDialogProps {
  readonly account: AccountSummary;
  readonly open: boolean;
  readonly onClose: () => void;
}

export function ChangeRoleDialog({ account, open, onClose }: ChangeRoleDialogProps) {
  const controller = useChangeRoleController({ account, onClose });

  return (
    <GlassModal open={open} onClose={onClose} title="Đổi vai trò" description={`${account.fullName} · ${account.email}`}>
      <form id="change-role-form" onSubmit={controller.handleSubmit} className="flex flex-col gap-4" noValidate>
        {controller.submitError ? <ErrorNotice error={controller.submitError} /> : null}

        <p className="text-sm text-slate-600">
          Vai trò hiện tại: <strong className="text-slate-900">{controller.currentRoleName}</strong>
        </p>

        <FormField label="Vai trò mới" htmlFor="change-role-id" error={controller.fieldErrors.roleId}>
          <GlassSelect
            id="change-role-id"
            value={controller.values.roleId}
            invalid={controller.fieldErrors.roleId !== undefined}
            onChange={(event) => controller.setRoleId(event.target.value)}
          >
            <option value="">-- Chọn vai trò --</option>
            {controller.roles.map((role) => (
              <option key={role.id} value={role.id}>
                {role.name}
              </option>
            ))}
          </GlassSelect>
        </FormField>

        <ul className="list-disc space-y-1 pl-5 text-xs text-slate-500">
          <li>Quyền mới có hiệu lực ngay ở lần thao tác kế tiếp của người đó, không cần đăng nhập lại.</li>
          <li>Chỉ đổi giữa các vai trò nhân viên. Muốn thành giáo viên hoặc học viên: đóng tài khoản này rồi tạo mới cùng email.</li>
          <li>Quản trị chi nhánh nghỉ việc: đổi sang vai trò Đã nghỉ việc, tài khoản chỉ còn xem được thông tin của mình.</li>
        </ul>
      </form>

      <footer className="flex justify-end gap-2">
        <GlassButton variant="ghost" onClick={onClose}>
          Huỷ
        </GlassButton>
        <GlassButton type="submit" form="change-role-form" loading={controller.isSubmitting}>
          Đổi vai trò
        </GlassButton>
      </footer>
    </GlassModal>
  );
}
