import {
  SUBMISSION_STATUS,
  type Assignment,
  type StudentAssignmentRow,
  type SubmissionStatus,
} from "@/entities/assessment";
import { useRecordResults } from "@/modules/assessment/api/use-assessment-mutations";
import { ENROLLMENT_STATUS } from "@/shared/api/schemas";
import { useCallback, useMemo, useState, type FormEvent } from "react";

export interface ResultDraft {
  readonly status: SubmissionStatus;
  readonly score: string;
  readonly feedback: string;
}

type DraftMap = Readonly<Record<string, ResultDraft>>;

function initialDrafts(assignment: Assignment, rows: readonly StudentAssignmentRow[]): DraftMap {
  return Object.fromEntries(
    rows.map((row) => {
      const cell = row.resultsByAssignmentId[assignment.id];
      return [
        row.enrollmentId,
        {
          status: cell?.status ?? SUBMISSION_STATUS.notSubmitted,
          score: cell?.score === null || cell?.score === undefined ? "" : String(cell.score),
          feedback: cell?.feedback ?? "",
        },
      ];
    }),
  );
}

/** Lỗi hiển thị trước khi gửi - backend vẫn kiểm lại (ASSESSMENT_INVALID_SCORE, ..._SCORE_WITHOUT_SUBMISSION). */
function validate(drafts: DraftMap, maxScore: number): string | null {
  for (const draft of Object.values(drafts)) {
    if (draft.score.trim() === "") {
      continue;
    }
    const score = Number(draft.score);
    if (!Number.isFinite(score) || score < 0 || score > maxScore) {
      return `Điểm phải nằm trong khoảng 0 - ${maxScore}`;
    }
    if (draft.status === SUBMISSION_STATUS.notSubmitted) {
      return "Học viên chưa nộp bài thì không thể có điểm";
    }
  }
  return null;
}

/** Nhập tình trạng nộp/điểm/nhận xét cho cả lớp ở một bài (Mandate #2). */
export function useAssignmentResultsForm(
  classId: string,
  assignment: Assignment,
  allRows: readonly StudentAssignmentRow[],
  onDone: () => void,
) {
  const recordResults = useRecordResults(classId);
  const rows = useMemo(
    () => allRows.filter((row) => row.enrollmentStatus === ENROLLMENT_STATUS.active),
    [allRows],
  );
  const [drafts, setDrafts] = useState<DraftMap>(() => initialDrafts(assignment, rows));
  const [validationError, setValidationError] = useState<string | null>(null);

  const update = useCallback((enrollmentId: string, patch: Partial<ResultDraft>) => {
    setDrafts((previous) => {
      const current = previous[enrollmentId];
      return current ? { ...previous, [enrollmentId]: { ...current, ...patch } } : previous;
    });
  }, []);

  const handleSubmit = useCallback(
    (event: FormEvent<HTMLFormElement>) => {
      event.preventDefault();
      const error = validate(drafts, assignment.maxScore);
      setValidationError(error);
      if (error !== null) {
        return;
      }
      const entries = Object.entries(drafts).map(([enrollmentId, draft]) => ({
        enrollmentId,
        status: draft.status,
        score: draft.score.trim() === "" ? null : Number(draft.score),
        feedback: draft.feedback.trim() === "" ? null : draft.feedback.trim(),
      }));
      recordResults.mutate({ assignmentId: assignment.id, entries }, { onSuccess: onDone });
    },
    [assignment.id, assignment.maxScore, drafts, onDone, recordResults],
  );

  return {
    rows,
    drafts,
    update,
    handleSubmit,
    validationError,
    submitError: recordResults.error,
    isSubmitting: recordResults.isPending,
  };
}
