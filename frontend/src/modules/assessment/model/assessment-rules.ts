import {
  ASSESSMENT_LIMITS,
  isMissingWork,
  SUBMISSION_STATUS,
  type Assignment,
  type StudentAssignmentRow,
  type SubmissionStatus,
} from "@/entities/assessment";
import { ENROLLMENT_STATUS } from "@/shared/api/schemas";

/** Nhánh hiển thị tách khỏi JSX (Mandate #3). */
type Tone = "neutral" | "positive" | "warning" | "danger" | "accent";

const SUBMISSION_TONE: Record<SubmissionStatus, Tone> = {
  [SUBMISSION_STATUS.submitted]: "positive",
  [SUBMISSION_STATUS.late]: "warning",
  [SUBMISSION_STATUS.notSubmitted]: "neutral",
};

export function submissionTone(status: SubmissionStatus): Tone {
  return SUBMISSION_TONE[status];
}

/** Số học viên đang học bị "thiếu bài" ở một bài: quá hạn mà chưa nộp. */
export function missingCount(assignment: Assignment, rows: readonly StudentAssignmentRow[], today: string): number {
  return rows.filter(
    (row) =>
      row.enrollmentStatus === ENROLLMENT_STATUS.active &&
      isMissingWork(assignment.dueDate, row.resultsByAssignmentId[assignment.id]?.status, today),
  ).length;
}

export function percentLabel(percent: number | null, emptyLabel: string): string {
  return percent === null ? emptyLabel : `${percent}%`;
}

/** Ngưỡng điểm/hoàn thành tốt - từ mức này trở lên tô xanh. */
const GOOD_PERCENT = 80;
const FAIR_PERCENT = 50;

/** Điểm cao nhất so với ngưỡng đạt của mini-task: đạt = xanh, còn lại theo mức điểm. */
export function masteryTone(best: number, passPercent: number): Tone {
  return best >= passPercent ? "positive" : percentTone(best);
}

export function percentTone(percent: number | null): Tone {
  if (percent === null) {
    return "neutral";
  }
  if (percent >= GOOD_PERCENT) {
    return "positive";
  }
  return percent >= FAIR_PERCENT ? "warning" : "danger";
}

/** Câu hỏi đang soạn trong form mini-task. `explanation` rỗng = không có lời giải. */
export interface QuestionDraft {
  readonly content: string;
  readonly options: readonly string[];
  readonly correctOptionIndex: number;
  readonly explanation: string;
}

export const EMPTY_QUESTION: QuestionDraft = { content: "", options: ["", ""], correctOptionIndex: 0, explanation: "" };

/** Câu chưa gõ gì (câu trống mặc định của form mới) - bị thay thế khi nhập nhanh. */
export function isBlankQuestion(question: QuestionDraft): boolean {
  return question.content.trim() === "" && question.options.every((option) => option.trim() === "");
}

/** Ngưỡng đạt hợp lệ là số nguyên trong khoảng cho phép. */
export function isValidPassPercent(value: string): boolean {
  const percent = Number(value);
  return (
    Number.isInteger(percent) && percent >= ASSESSMENT_LIMITS.minPassPercent && percent <= ASSESSMENT_LIMITS.maxPassPercent
  );
}

/** Trả số thứ tự (đếm từ 1) của câu đầu tiên chưa hợp lệ, hoặc null nếu mọi câu đều hợp lệ. */
export function firstInvalidQuestion(questions: readonly QuestionDraft[]): number | null {
  const index = questions.findIndex(
    (question) =>
      question.content.trim() === "" ||
      question.options.some((option) => option.trim() === "") ||
      question.correctOptionIndex >= question.options.length,
  );
  return index === -1 ? null : index + 1;
}

/** Lỗi đầu tiên của form mini-task trước khi gửi, hoặc null nếu hợp lệ. */
export function miniTaskFormError(title: string, passPercent: string, questions: readonly QuestionDraft[]): string | null {
  if (title.trim() === "") {
    return "Nhập tên mini-task";
  }
  if (!isValidPassPercent(passPercent)) {
    return `Ngưỡng đạt phải là số nguyên từ ${ASSESSMENT_LIMITS.minPassPercent} đến ${ASSESSMENT_LIMITS.maxPassPercent}`;
  }
  const invalid = firstInvalidQuestion(questions);
  if (invalid === null) {
    return null;
  }
  return `Câu ${invalid} còn thiếu nội dung hoặc phương án`;
}
