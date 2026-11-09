import type { TeacherPay } from "@/entities/teacher";
import { useEditTeacherPayController } from "@/modules/teachers/hooks/use-edit-teacher-pay-controller";
import { toPayFieldSetter } from "@/modules/teachers/model/teachers-forms";
import { TeacherPayFields } from "@/modules/teachers/ui/teacher-pay-fields";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassModal } from "@/shared/ui/glass-modal";

export interface EditTeacherPayDialogProps {
  readonly pay: TeacherPay;
  readonly open: boolean;
  readonly onClose: () => void;
}

export function EditTeacherPayDialog({ pay, open, onClose }: EditTeacherPayDialogProps) {
  const form = useEditTeacherPayController({ pay, onClose });

  return (
    <GlassModal
      open={open}
      onClose={onClose}
      title="Thiết lập lương"
      description="Áp dụng cho kỳ còn nháp khi tính lại. Kỳ đã duyệt giữ nguyên số liệu đã chốt."
    >
      <form id="edit-teacher-pay-form" onSubmit={form.handleSubmit} className="flex flex-col gap-4" noValidate>
        {form.submitError ? <ErrorNotice error={form.submitError} /> : null}
        <TeacherPayFields
          values={form.values}
          fieldErrors={form.fieldErrors}
          setValue={toPayFieldSetter(form.setValue)}
        />
      </form>

      <footer className="flex justify-end gap-2">
        <GlassButton variant="ghost" onClick={onClose}>
          Huỷ
        </GlassButton>
        <GlassButton type="submit" form="edit-teacher-pay-form" loading={form.isSubmitting}>
          Lưu
        </GlassButton>
      </footer>
    </GlassModal>
  );
}
