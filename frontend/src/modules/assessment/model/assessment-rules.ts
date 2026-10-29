import { SUBMISSION_STATUS, type SubmissionStatus } from "@/entities/assessment";

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

export function percentLabel(percent: number | null, emptyLabel: string): string {
  return percent === null ? emptyLabel : `${percent}%`;
}

/** Ngưỡng điểm/hoàn thành tốt - từ mức này trở lên tô xanh. */
const GOOD_PERCENT = 80;
const FAIR_PERCENT = 50;

export function percentTone(percent: number | null): Tone {
  if (percent === null) {
    return "neutral";
  }
  if (percent >= GOOD_PERCENT) {
    return "positive";
  }
  return percent >= FAIR_PERCENT ? "warning" : "danger";
}

/** Câu hỏi đang soạn trong form mini-task. */
export interface QuestionDraft {
  readonly content: string;
  readonly options: readonly string[];
  readonly correctOptionIndex: number;
}

export const EMPTY_QUESTION: QuestionDraft = { content: "", options: ["", ""], correctOptionIndex: 0 };

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
export function miniTaskFormError(title: string, questions: readonly QuestionDraft[]): string | null {
  if (title.trim() === "") {
    return "Nhập tên mini-task";
  }
  const invalid = firstInvalidQuestion(questions);
  if (invalid === null) {
    return null;
  }
  return `Câu ${invalid} còn thiếu nội dung hoặc phương án`;
}
