import { useCreateTeacherController } from "@/modules/teachers/hooks/use-create-teacher-controller";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { FormField } from "@/shared/ui/form-field";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassInput } from "@/shared/ui/glass-input";
import { GlassModal } from "@/shared/ui/glass-modal";
import { GlassSelect } from "@/shared/ui/glass-select";

export interface CreateTeacherDialogProps {
  readonly open: boolean;
  readonly onClose: () => void;
}

/** Tạo giáo viên MỘT bước: tài khoản đăng nhập (email mời + mật khẩu tạm) và hồ sơ giáo viên cùng lúc. */
export function CreateTeacherDialog({ open, onClose }: CreateTeacherDialogProps) {
  const controller = useCreateTeacherController({ onClose });
  const { values, fieldErrors, setValue } = controller;

  return (
    <GlassModal
      open={open}
      onClose={onClose}
      title="Thêm giáo viên"
      description="Hệ thống tạo tài khoản và gửi email kèm mật khẩu tạm (hiệu lực 7 ngày) cho giáo viên."
    >
      <form id="create-teacher-form" onSubmit={controller.handleSubmit} className="flex flex-col gap-4" noValidate>
        {controller.submitError ? <ErrorNotice error={controller.submitError} /> : null}

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <FormField label="Họ tên" htmlFor="teacher-name" error={fieldErrors.fullName}>
            <GlassInput
              id="teacher-name"
              autoFocus
              value={values.fullName}
              invalid={fieldErrors.fullName !== undefined}
              onChange={(event) => setValue("fullName", event.target.value)}
            />
          </FormField>
          <FormField label="Email đăng nhập" htmlFor="teacher-email" error={fieldErrors.email}>
            <GlassInput
              id="teacher-email"
              type="email"
              value={values.email}
              invalid={fieldErrors.email !== undefined}
              onChange={(event) => setValue("email", event.target.value)}
            />
          </FormField>
        </div>

        <FormField label="Chi nhánh chủ quản" htmlFor="teacher-branch" hint="Giáo viên vẫn dạy được lớp ở chi nhánh khác.">
          <GlassSelect
            id="teacher-branch"
            value={values.homeBranchId}
            onChange={(event) => setValue("homeBranchId", event.target.value)}
          >
            <option value="">-- Cấp tổ chức --</option>
            {controller.branches.map((branch) => (
              <option key={branch.id} value={branch.id}>
                {branch.name}
              </option>
            ))}
          </GlassSelect>
        </FormField>

        <FormField label="Môn/chuyên môn dạy" htmlFor="teacher-subjects" hint="Cách nhau bởi dấu phẩy, ví dụ: Tiếng Anh, IELTS">
          <GlassInput
            id="teacher-subjects"
            value={values.subjects}
            onChange={(event) => setValue("subjects", event.target.value)}
          />
        </FormField>

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <FormField label="Số điện thoại" htmlFor="teacher-phone" hint="Không bắt buộc.">
            <GlassInput
              id="teacher-phone"
              value={values.phone}
              onChange={(event) => setValue("phone", event.target.value)}
            />
          </FormField>
          <FormField label="Giới thiệu ngắn" htmlFor="teacher-bio" hint="Không bắt buộc.">
            <GlassInput id="teacher-bio" value={values.bio} onChange={(event) => setValue("bio", event.target.value)} />
          </FormField>
        </div>
      </form>

      <footer className="flex justify-end gap-2">
        <GlassButton variant="ghost" onClick={onClose}>
          Huỷ
        </GlassButton>
        <GlassButton type="submit" form="create-teacher-form" loading={controller.isSubmitting}>
          Thêm giáo viên
        </GlassButton>
      </footer>
    </GlassModal>
  );
}
