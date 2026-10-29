import {
  SUBMISSION_STATUS_LABEL,
  SUBMISSION_STATUS_ORDER,
  type Assignment,
  type StudentAssignmentRow,
  type SubmissionStatus,
} from "@/entities/assessment";
import { useAssignmentResultsForm } from "@/modules/assessment/hooks/use-assignment-results-form";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassInput } from "@/shared/ui/glass-input";
import { GlassModal } from "@/shared/ui/glass-modal";
import { GlassSelect } from "@/shared/ui/glass-select";

export interface AssignmentResultsDialogProps {
  readonly classId: string;
  readonly assignment: Assignment;
  readonly rows: readonly StudentAssignmentRow[];
  readonly onClose: () => void;
}

/** Nhập tình trạng nộp, điểm và nhận xét cho từng học viên đang học ở một bài. */
export function AssignmentResultsDialog({ classId, assignment, rows, onClose }: AssignmentResultsDialogProps) {
  const form = useAssignmentResultsForm(classId, assignment, rows, onClose);

  return (
    <GlassModal open onClose={onClose} title={`Kết quả: ${assignment.title}`}
      description={`Thang điểm ${assignment.maxScore}. Để trống điểm nếu chưa chấm.`}>
      <form id="assignment-results-form" onSubmit={form.handleSubmit} className="flex flex-col gap-3" noValidate>
        {form.validationError ? <p className="text-sm text-rose-600">{form.validationError}</p> : null}
        {form.submitError ? <ErrorNotice error={form.submitError} /> : null}
        <ul className="flex max-h-[50vh] flex-col gap-3 overflow-y-auto">
          {form.rows.map((row) => {
            const draft = form.drafts[row.enrollmentId];
            if (!draft) {
              return null;
            }
            return (
              <li key={row.enrollmentId} className="grid grid-cols-1 items-center gap-2 sm:grid-cols-[1.4fr_1fr_0.7fr_1.6fr]">
                <span className="text-sm text-mist-100">{row.studentName ?? "Học viên"}</span>
                <GlassSelect aria-label="Tình trạng nộp" value={draft.status}
                  onChange={(event) => form.update(row.enrollmentId, { status: event.target.value as SubmissionStatus })}>
                  {SUBMISSION_STATUS_ORDER.map((status) => (
                    <option key={status} value={status}>{SUBMISSION_STATUS_LABEL[status]}</option>
                  ))}
                </GlassSelect>
                <GlassInput aria-label="Điểm" type="number" min={0} max={assignment.maxScore} step={0.01}
                  value={draft.score} onChange={(event) => form.update(row.enrollmentId, { score: event.target.value })} />
                <GlassInput aria-label="Nhận xét" placeholder="Nhận xét" value={draft.feedback}
                  onChange={(event) => form.update(row.enrollmentId, { feedback: event.target.value })} />
              </li>
            );
          })}
        </ul>
      </form>
      <footer className="flex justify-end gap-2">
        <GlassButton variant="ghost" onClick={onClose}>Huỷ</GlassButton>
        <GlassButton type="submit" form="assignment-results-form" loading={form.isSubmitting}>Lưu kết quả</GlassButton>
      </footer>
    </GlassModal>
  );
}
