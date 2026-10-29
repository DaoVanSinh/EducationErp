import type { AttemptResult, Question } from "@/entities/assessment";
import { cx } from "@/shared/lib/class-names";

export interface PracticeQuestionProps {
  readonly index: number;
  readonly question: Question;
  readonly selected: number | undefined;
  readonly review: AttemptResult["results"][number] | undefined;
  readonly onChoose: (questionId: string, optionIndex: number) => void;
}

/** Màu một phương án sau khi nộp: đúng = xanh, chọn sai = đỏ. Trước khi nộp chỉ tô phương án đang chọn. */
function optionClass(optionIndex: number, selected: number | undefined, review: PracticeQuestionProps["review"]): string {
  if (review) {
    if (optionIndex === review.correctOptionIndex) {
      return "border-emerald-300 bg-emerald-50 text-emerald-700";
    }
    if (optionIndex === review.selectedOptionIndex) {
      return "border-rose-300 bg-rose-50 text-rose-600";
    }
    return "border-slate-200 bg-white/60 text-slate-600";
  }
  if (optionIndex === selected) {
    return "border-orange-300 bg-orange-100 text-orange-700";
  }
  return "border-slate-200 bg-white/60 text-slate-600 hover:bg-white";
}

/** Một câu trắc nghiệm khi làm bài; sau khi nộp hiện đáp án đúng/sai, không cho chọn lại. */
export function PracticeQuestion({ index, question, selected, review, onChoose }: PracticeQuestionProps) {
  return (
    <fieldset className="glass flex flex-col gap-2 rounded-2xl p-4">
      <legend className="sr-only">Câu {index + 1}</legend>
      <p className="text-sm font-semibold text-mist-100">
        Câu {index + 1}. {question.content}
      </p>
      <div className="grid grid-cols-1 gap-2 sm:grid-cols-2" role="radiogroup">
        {question.options.map((option, optionIndex) => (
          <button
            key={optionIndex}
            type="button"
            role="radio"
            aria-checked={selected === optionIndex}
            disabled={review !== undefined}
            onClick={() => onChoose(question.id, optionIndex)}
            className={cx("rounded-xl border px-3 py-2 text-left text-sm transition", optionClass(optionIndex, selected, review))}
          >
            {String.fromCharCode(65 + optionIndex)}. {option}
          </button>
        ))}
      </div>
    </fieldset>
  );
}
