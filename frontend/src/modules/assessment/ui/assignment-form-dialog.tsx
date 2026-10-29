import { ASSIGNMENT_TYPE, ASSIGNMENT_TYPE_LABEL, type AssignmentType } from "@/entities/assessment";
import { useAssignmentForm } from "@/modules/assessment/hooks/use-assignment-form";
import type { AssignmentFormTarget } from "@/modules/assessment/hooks/use-class-assignments-controller";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { FormField } from "@/shared/ui/form-field";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassInput } from "@/shared/ui/glass-input";
import { GlassModal } from "@/shared/ui/glass-modal";
import { GlassSelect } from "@/shared/ui/glass-select";

export interface AssignmentFormDialogProps {
  readonly classId: string;
  readonly target: AssignmentFormTarget;
  readonly onClose: () => void;
}

const TYPES: readonly AssignmentType[] = [ASSIGNMENT_TYPE.homework, ASSIGNMENT_TYPE.miniTest];

/** Giao bài mới hoặc sửa / tắt một bài. */
export function AssignmentFormDialog({ classId, target, onClose }: AssignmentFormDialogProps) {
  const { form, isNew } = useAssignmentForm(classId, target, onClose);

  return (
    <GlassModal
      open
      onClose={onClose}
      title={isNew ? "Giao bài tập / mini-test" : "Sửa bài"}
      description="Bài làm ngoài hệ thống; sau khi học viên nộp, bạn nhập tình trạng nộp và điểm."
    >
      <form id="assignment-form" onSubmit={form.handleSubmit} className="flex flex-col gap-4" noValidate>
        {form.submitError ? <ErrorNotice error={form.submitError} /> : null}
        <FormField label="Tên bài" htmlFor="assignment-title" error={form.fieldErrors.title}>
          <GlassInput id="assignment-title" autoFocus value={form.values.title}
            invalid={form.fieldErrors.title !== undefined} onChange={(event) => form.setValue("title", event.target.value)} />
        </FormField>
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
          <FormField label="Loại" htmlFor="assignment-type">
            <GlassSelect id="assignment-type" value={form.values.type}
              onChange={(event) => form.setValue("type", event.target.value as AssignmentType)}>
              {TYPES.map((type) => (
                <option key={type} value={type}>{ASSIGNMENT_TYPE_LABEL[type]}</option>
              ))}
            </GlassSelect>
          </FormField>
          <FormField label="Hạn nộp" htmlFor="assignment-due">
            <GlassInput id="assignment-due" type="date" value={form.values.dueDate}
              onChange={(event) => form.setValue("dueDate", event.target.value)} />
          </FormField>
          <FormField label="Thang điểm" htmlFor="assignment-max" error={form.fieldErrors.maxScore}>
            <GlassInput id="assignment-max" type="number" min={0.01} step={0.01} value={form.values.maxScore}
              invalid={form.fieldErrors.maxScore !== undefined} onChange={(event) => form.setValue("maxScore", event.target.value)} />
          </FormField>
        </div>
        <FormField label="Mô tả / yêu cầu" htmlFor="assignment-description">
          <GlassInput id="assignment-description" value={form.values.description}
            onChange={(event) => form.setValue("description", event.target.value)} />
        </FormField>
        {isNew ? null : (
          <label className="flex items-center gap-2 text-sm text-mist-100">
            <input type="checkbox" checked={form.values.active} onChange={(event) => form.setValue("active", event.target.checked)} />
            Bài đang bật (học viên thấy và được tính vào tỷ lệ hoàn thành)
          </label>
        )}
      </form>
      <footer className="flex justify-end gap-2">
        <GlassButton variant="ghost" onClick={onClose}>Huỷ</GlassButton>
        <GlassButton type="submit" form="assignment-form" loading={form.isSubmitting}>
          {isNew ? "Giao bài" : "Lưu"}
        </GlassButton>
      </footer>
    </GlassModal>
  );
}
