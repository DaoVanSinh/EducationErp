import { useCreateStudentController } from "@/modules/students/hooks/use-create-student-controller";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { FormField } from "@/shared/ui/form-field";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassInput } from "@/shared/ui/glass-input";
import { GlassModal } from "@/shared/ui/glass-modal";
import { GlassSelect } from "@/shared/ui/glass-select";

export interface CreateStudentDialogProps {
  readonly open: boolean;
  readonly onClose: () => void;
}

/** Tạo học viên MỘT bước: tài khoản đăng nhập (email mời + mật khẩu tạm) và hồ sơ học viên cùng lúc. */
export function CreateStudentDialog({ open, onClose }: CreateStudentDialogProps) {
  const controller = useCreateStudentController({ onClose });
  const { values, fieldErrors, setValue } = controller;

  return (
    <GlassModal
      open={open}
      onClose={onClose}
      title="Thêm học viên"
      description="Hệ thống tạo tài khoản và gửi email kèm mật khẩu tạm (hiệu lực 7 ngày) cho học viên."
    >
      <form id="create-student-form" onSubmit={controller.handleSubmit} className="flex flex-col gap-4" noValidate>
        {controller.submitError ? <ErrorNotice error={controller.submitError} /> : null}

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <FormField label="Họ tên" htmlFor="student-name" error={fieldErrors.fullName}>
            <GlassInput
              id="student-name"
              autoFocus
              value={values.fullName}
              invalid={fieldErrors.fullName !== undefined}
              onChange={(event) => setValue("fullName", event.target.value)}
            />
          </FormField>
          <FormField label="Email đăng nhập" htmlFor="student-email" error={fieldErrors.email}>
            <GlassInput
              id="student-email"
              type="email"
              value={values.email}
              invalid={fieldErrors.email !== undefined}
              onChange={(event) => setValue("email", event.target.value)}
            />
          </FormField>
        </div>

        <BranchField
          canChoose={controller.canChooseBranch}
          value={values.homeBranchId}
          branches={controller.branches}
          fixedLabel={controller.fixedBranchLabel}
          onChange={(branchId) => setValue("homeBranchId", branchId)}
        />

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <FormField label="Ngày sinh" htmlFor="student-dob" hint="Không bắt buộc.">
            <GlassInput
              id="student-dob"
              type="date"
              value={values.dateOfBirth}
              onChange={(event) => setValue("dateOfBirth", event.target.value)}
            />
          </FormField>
          <FormField label="Số điện thoại" htmlFor="student-phone" hint="Không bắt buộc.">
            <GlassInput
              id="student-phone"
              value={values.phone}
              onChange={(event) => setValue("phone", event.target.value)}
            />
          </FormField>
        </div>

        <FormField label="Biết đến trung tâm qua đâu" htmlFor="student-source" hint="Không bắt buộc.">
          <GlassInput
            id="student-source"
            value={values.sourceChannel}
            onChange={(event) => setValue("sourceChannel", event.target.value)}
          />
        </FormField>
      </form>

      <footer className="flex justify-end gap-2">
        <GlassButton variant="ghost" onClick={onClose}>
          Huỷ
        </GlassButton>
        <GlassButton type="submit" form="create-student-form" loading={controller.isSubmitting}>
          Thêm học viên
        </GlassButton>
      </footer>
    </GlassModal>
  );
}

interface BranchFieldProps {
  readonly canChoose: boolean;
  readonly value: string;
  readonly branches: readonly { readonly id: string; readonly name: string }[];
  readonly fixedLabel: string;
  readonly onChange: (branchId: string) => void;
}

function BranchField({ canChoose, value, branches, fixedLabel, onChange }: BranchFieldProps) {
  if (!canChoose) {
    return (
      <FormField label="Chi nhánh" htmlFor="student-branch" hint="Học viên thuộc chi nhánh bạn đang quản lý.">
        <GlassInput id="student-branch" value={fixedLabel} readOnly disabled />
      </FormField>
    );
  }
  return (
    <FormField label="Chi nhánh" htmlFor="student-branch" hint="Chi nhánh học viên theo học.">
      <GlassSelect id="student-branch" value={value} onChange={(event) => onChange(event.target.value)}>
        <option value="">-- Chưa chọn --</option>
        {branches.map((branch) => (
          <option key={branch.id} value={branch.id}>
            {branch.name}
          </option>
        ))}
      </GlassSelect>
    </FormField>
  );
}
