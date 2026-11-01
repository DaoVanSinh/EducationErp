import type { OrderedQuestion, QuestionReview } from "@/modules/learning/model/practice-order";
import { cx } from "@/shared/lib/class-names";
import { Lightbulb } from "lucide-react";

export interface PracticeQuestionProps {
  readonly index: number;
  readonly item: OrderedQuestion;
  readonly selected: number | undefined;
  readonly review: QuestionReview | undefined;
  readonly onChoose: (questionId: string, optionIndex: number) => void;
}

const LETTER_A = 65;

/** Màu một phương án sau khi nộp: đúng = xanh, chọn sai = đỏ. Trước khi nộp chỉ tô phương án đang chọn. */
function optionClass(optionIndex: number, selected: number | undefined, review: QuestionReview | undefined): string {
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

/**
 * Một câu trắc nghiệm khi làm bài, phương án theo thứ tự đã xáo của lượt này (chữ cái theo vị trí hiển thị,
 * giá trị là chỉ số gốc). Sau khi nộp hiện đúng/sai và lời giải, không cho chọn lại.
 */
export function PracticeQuestion({ index, item, selected, review, onChoose }: PracticeQuestionProps) {
  const { question, optionOrder } = item;
  return (
    <fieldset className="glass flex flex-col gap-2 rounded-2xl p-4">
      <legend className="sr-only">Câu {index + 1}</legend>
      <p className="text-sm font-semibold text-mist-100">
        Câu {index + 1}. {question.content}
      </p>
      <div className="grid grid-cols-1 gap-2 sm:grid-cols-2" role="radiogroup">
        {optionOrder.map((optionIndex, position) => (
          <button
            key={optionIndex}
            type="button"
            role="radio"
            aria-checked={selected === optionIndex}
            disabled={review !== undefined}
            onClick={() => onChoose(question.id, optionIndex)}
            className={cx("rounded-xl border px-3 py-2 text-left text-sm transition", optionClass(optionIndex, selected, review))}
          >
            {String.fromCharCode(LETTER_A + position)}. {question.options[optionIndex]}
          </button>
        ))}
      </div>
      {review?.explanation ? (
        <p className="flex items-start gap-2 rounded-xl bg-amber-50 px-3 py-2 text-xs text-amber-800">
          <Lightbulb size={14} className="mt-0.5 shrink-0" aria-hidden />
          <span>{review.explanation}</span>
        </p>
      ) : null}
    </fieldset>
  );
}
