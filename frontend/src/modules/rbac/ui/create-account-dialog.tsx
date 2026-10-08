import { useCreateAccountController } from "@/modules/rbac/hooks/use-create-account-controller";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { FormField } from "@/shared/ui/form-field";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassInput } from "@/shared/ui/glass-input";
import { GlassModal } from "@/shared/ui/glass-modal";
import { GlassSelect } from "@/shared/ui/glass-select";

export interface CreateAccountDialogProps {
  readonly open: boolean;
  readonly onClose: () => void;
}

export function CreateAccountDialog({ open, onClose }: CreateAccountDialogProps) {
  const { values, fieldErrors, submitError, isSubmitting, branches, roles, handleSubmit, setValue } =
    useCreateAccountController({ onClose });

  return (
    <GlassModal
      open={open}
      onClose={onClose}
      title="Tạo tài khoản"
      description="Hệ thống sẽ gửi email kèm mật khẩu tạm, hiệu lực 7 ngày."
    >
      <form id="create-account-form" onSubmit={handleSubmit} className="flex flex-col gap-4" noValidate>
        {submitError ? <ErrorNotice error={submitError} /> : null}

        <FormField label="Email" htmlFor="create-account-email" error={fieldErrors.email}>
          <GlassInput
            id="create-account-email"
            type="email"
            autoFocus
            value={values.email}
            invalid={fieldErrors.email !== undefined}
            onChange={(event) => setValue("email", event.target.value)}
          />
        </FormField>

        <FormField label="Họ tên" htmlFor="create-account-name" error={fieldErrors.fullName}>
          <GlassInput
            id="create-account-name"
            value={values.fullName}
            invalid={fieldErrors.fullName !== undefined}
            onChange={(event) => setValue("fullName", event.target.value)}
          />
        </FormField>

        <FormField label="Vai trò" htmlFor="create-account-role" error={fieldErrors.roleId}>
          <GlassSelect
            id="create-account-role"
            value={values.roleId}
            invalid={fieldErrors.roleId !== undefined}
            onChange={(event) => setValue("roleId", event.target.value)}
          >
            <option value="">-- Chọn vai trò --</option>
            {roles.map((role) => (
              <option key={role.id} value={role.id}>
                {role.name}
              </option>
            ))}
          </GlassSelect>
        </FormField>

        <FormField label="Chi nhánh" htmlFor="create-account-branch" hint="Bỏ trống nếu là tài khoản cấp tổ chức.">
          <GlassSelect
            id="create-account-branch"
            value={values.homeBranchId}
            onChange={(event) => setValue("homeBranchId", event.target.value)}
          >
            <option value="">-- Cấp tổ chức --</option>
            {branches.map((branch) => (
              <option key={branch.id} value={branch.id}>
                {branch.name}
              </option>
            ))}
          </GlassSelect>
        </FormField>
      </form>

      <footer className="flex justify-end gap-2">
        <GlassButton variant="ghost" onClick={onClose}>
          Huỷ
        </GlassButton>
        <GlassButton type="submit" form="create-account-form" loading={isSubmitting}>
          Tạo tài khoản
        </GlassButton>
      </footer>
    </GlassModal>
  );
}
