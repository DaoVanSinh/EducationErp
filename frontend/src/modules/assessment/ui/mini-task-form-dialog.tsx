import { ASSESSMENT_LIMITS } from "@/entities/assessment";
import type { MiniTaskFormTarget } from "@/modules/assessment/hooks/use-class-mini-tasks-controller";
import { useMiniTaskForm } from "@/modules/assessment/hooks/use-mini-task-form";
import { QuestionEditor } from "@/modules/assessment/ui/question-editor";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { FormField } from "@/shared/ui/form-field";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassInput } from "@/shared/ui/glass-input";
import { GlassModal } from "@/shared/ui/glass-modal";
import { Plus } from "lucide-react";

export interface MiniTaskFormDialogProps {
  readonly classId: string;
  readonly target: MiniTaskFormTarget;
  readonly onClose: () => void;
}

/** Soạn hoặc sửa một mini-task trắc nghiệm - học viên làm trực tiếp trên hệ thống, chấm tự động. */
export function MiniTaskFormDialog({ classId, target, onClose }: MiniTaskFormDialogProps) {
  const form = useMiniTaskForm(classId, target, onClose);

  return (
    <GlassModal
      open
      onClose={onClose}
      title={form.isNew ? "Tạo mini-task" : "Sửa mini-task"}
      description="Chọn nút tròn trước phương án đúng. Học viên chỉ thấy đáp án sau khi nộp bài."
    >
      <form id="mini-task-form" onSubmit={form.handleSubmit} className="flex flex-col gap-4" noValidate>
        {form.validationError ? <p className="text-sm text-rose-600">{form.validationError}</p> : null}
        {form.submitError ? <ErrorNotice error={form.submitError} /> : null}
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <FormField label="Tên mini-task" htmlFor="mini-task-title">
            <GlassInput id="mini-task-title" autoFocus value={form.title} onChange={(event) => form.setTitle(event.target.value)} />
          </FormField>
          <FormField label="Mô tả" htmlFor="mini-task-description">
            <GlassInput id="mini-task-description" value={form.description}
              onChange={(event) => form.setDescription(event.target.value)} />
          </FormField>
        </div>
        {form.isNew ? null : (
          <label className="flex items-center gap-2 text-sm text-mist-100">
            <input type="checkbox" checked={form.active} onChange={(event) => form.setActive(event.target.checked)} />
            Đang mở cho học viên luyện tập
          </label>
        )}
        <div className="flex max-h-[50vh] flex-col gap-3 overflow-y-auto">
          {form.questions.map((question, index) => (
            <QuestionEditor key={index} index={index} question={question} form={form} />
          ))}
        </div>
        <GlassButton variant="secondary" size="sm" onClick={form.addQuestion}
          disabled={form.questions.length >= ASSESSMENT_LIMITS.maxQuestions} icon={<Plus size={14} aria-hidden />}>
          Thêm câu hỏi
        </GlassButton>
      </form>
      <footer className="flex justify-end gap-2">
        <GlassButton variant="ghost" onClick={onClose}>Huỷ</GlassButton>
        <GlassButton type="submit" form="mini-task-form" loading={form.isSubmitting}>
          {form.isNew ? "Tạo mini-task" : "Lưu"}
        </GlassButton>
      </footer>
    </GlassModal>
  );
}
