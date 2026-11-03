import { ASSESSMENT_LIMITS, ASSIGNMENT_TYPE_LABEL, type MyAssignmentItem } from "@/entities/assessment";
import { useAssignmentSubmissionForm } from "@/modules/learning/hooks/use-assignment-submission-form";
import { isPastDue } from "@/modules/learning/model/learning-rules";
import { formatter } from "@/shared/lib/format";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { FormField } from "@/shared/ui/form-field";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassModal } from "@/shared/ui/glass-modal";

export interface AssignmentSubmissionDialogProps {
  readonly assignment: MyAssignmentItem;
  readonly today: string;
  readonly onClose: () => void;
}

function dueLabel(dueDate: string | null): string {
  return dueDate === null ? "Không đặt hạn" : `Hạn nộp ${formatter.date(dueDate)}`;
}

/** Xem đề và nộp bài. Bài đã chấm hoặc không còn nhận thì chỉ xem lại nội dung đã nộp. */
export function AssignmentSubmissionDialog({ assignment, today, onClose }: AssignmentSubmissionDialogProps) {
  return (
    <GlassModal
      open
      onClose={onClose}
      title={assignment.title}
      description={`${ASSIGNMENT_TYPE_LABEL[assignment.type]} · ${dueLabel(assignment.dueDate)} · thang điểm ${assignment.maxScore}`}
    >
      {assignment.description ? (
        <p className="text-sm whitespace-pre-wrap text-mist-100">{assignment.description}</p>
      ) : null}
      {assignment.canSubmit ? (
        <SubmissionForm assignment={assignment} today={today} onClose={onClose} />
      ) : (
        <SubmittedWork assignment={assignment} onClose={onClose} />
      )}
    </GlassModal>
  );
}

function SubmissionForm({ assignment, today, onClose }: AssignmentSubmissionDialogProps) {
  const form = useAssignmentSubmissionForm(assignment, onClose);
  const hint = isPastDue(assignment.dueDate, today)
    ? "Đã quá hạn: bài vẫn được nhận và ghi là nộp muộn."
    : "Bạn có thể nộp lại cho tới khi giáo viên chấm điểm.";

  return (
    <>
      <form id="assignment-submission-form" onSubmit={form.handleSubmit} className="flex flex-col gap-3" noValidate>
        {form.submitError ? <ErrorNotice error={form.submitError} /> : null}
        <FormField label="Bài làm" htmlFor="assignment-submission-content" hint={hint} error={form.fieldErrors.content}>
          <textarea
            id="assignment-submission-content"
            rows={8}
            maxLength={ASSESSMENT_LIMITS.maxSubmissionLength}
            value={form.values.content}
            onChange={(event) => form.setValue("content", event.target.value)}
            placeholder="Nhập câu trả lời, hoặc dán đường dẫn tới bài làm"
            className="w-full rounded-2xl border border-slate-200/90 bg-white/75 p-3 text-sm text-slate-800 focus:border-orange-400 focus:outline-none focus:ring-2 focus:ring-orange-300/30"
          />
        </FormField>
      </form>
      <footer className="flex justify-end gap-2">
        <GlassButton variant="ghost" onClick={onClose}>Huỷ</GlassButton>
        <GlassButton type="submit" form="assignment-submission-form" loading={form.isSubmitting}>Nộp bài</GlassButton>
      </footer>
    </>
  );
}

function SubmittedWork({ assignment, onClose }: { readonly assignment: MyAssignmentItem; readonly onClose: () => void }) {
  return (
    <>
      {assignment.submissionContent ? (
        <div className="flex flex-col gap-1">
          <p className="text-xs text-mist-400">
            {assignment.submittedAt ? `Nộp lúc ${formatter.dateTime(assignment.submittedAt)}` : "Bài đã nộp"}
          </p>
          <p className="max-h-[40vh] overflow-y-auto rounded-2xl bg-white/60 p-3 text-sm whitespace-pre-wrap text-mist-100">
            {assignment.submissionContent}
          </p>
        </div>
      ) : null}
      {assignment.score === null ? null : (
        <p className="text-sm font-semibold text-mist-100">Điểm: {assignment.score}/{assignment.maxScore}</p>
      )}
      {assignment.feedback ? <p className="text-sm text-mist-100">Nhận xét: {assignment.feedback}</p> : null}
      <footer className="flex justify-end">
        <GlassButton variant="ghost" onClick={onClose}>Đóng</GlassButton>
      </footer>
    </>
  );
}
