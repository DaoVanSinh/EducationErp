import type { TeacherProfileSummary } from "@/entities/teacher";
import { useLeaveTeacherForm } from "@/modules/teachers/hooks/use-leave-teacher-form";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { FormField } from "@/shared/ui/form-field";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassInput } from "@/shared/ui/glass-input";
import { GlassModal } from "@/shared/ui/glass-modal";

export interface LeaveTeacherDialogProps {
  readonly teacher: TeacherProfileSummary;
  readonly open: boolean;
  readonly onClose: () => void;
}

export function LeaveTeacherDialog({ teacher, open, onClose }: LeaveTeacherDialogProps) {
  const form = useLeaveTeacherForm(teacher.id, onClose);

  return (
    <GlassModal
      open={open}
      onClose={onClose}
      title={`Cho nghỉ ${teacher.fullName ?? "giáo viên"}`}
      description="Lương tháng dừng từ sau ngày này. Tài khoản vẫn đăng nhập để xem lớp đã dạy và phiếu lương cũ cho tới khi admin đóng. Phải bàn giao hết lớp mở trước."
    >
      <form id="leave-teacher-form" onSubmit={form.handleSubmit} className="flex flex-col gap-4" noValidate>
        {form.submitError ? <ErrorNotice error={form.submitError} /> : null}
        <FormField label="Ngày làm việc cuối" htmlFor="leave-teacher-date" error={form.fieldErrors.lastWorkDate}>
          <GlassInput
            id="leave-teacher-date"
            type="date"
            value={form.values.lastWorkDate}
            invalid={form.fieldErrors.lastWorkDate !== undefined}
            onChange={(event) => form.setValue("lastWorkDate", event.target.value)}
          />
        </FormField>
      </form>

      <footer className="flex justify-end gap-2">
        <GlassButton variant="ghost" onClick={onClose}>
          Huỷ
        </GlassButton>
        <GlassButton type="submit" form="leave-teacher-form" loading={form.isSubmitting}>
          Cho nghỉ
        </GlassButton>
      </footer>
    </GlassModal>
  );
}
