import { ASSESSMENT_LIMITS } from "@/entities/assessment";
import type { MiniTaskForm } from "@/modules/assessment/hooks/use-mini-task-form";
import type { QuestionDraft } from "@/modules/assessment/model/assessment-rules";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassInput } from "@/shared/ui/glass-input";
import { Plus, Trash2 } from "lucide-react";

export interface QuestionEditorProps {
  readonly index: number;
  readonly question: QuestionDraft;
  readonly form: MiniTaskForm;
}

/** Soạn một câu trắc nghiệm: nội dung, 2-6 phương án, chọn đáp án đúng bằng nút tròn. */
export function QuestionEditor({ index, question, form }: QuestionEditorProps) {
  return (
    <fieldset className="glass flex flex-col gap-2 rounded-2xl p-3">
      <legend className="sr-only">Câu {index + 1}</legend>
      <div className="flex items-center gap-2">
        <span className="text-xs font-semibold text-mist-400">Câu {index + 1}</span>
        <GlassInput
          aria-label={`Nội dung câu ${index + 1}`}
          placeholder="Nội dung câu hỏi"
          value={question.content}
          onChange={(event) => form.updateQuestion(index, { content: event.target.value })}
        />
        <GlassButton variant="ghost" size="sm" onClick={() => form.removeQuestion(index)}
          disabled={form.questions.length <= 1} icon={<Trash2 size={14} aria-hidden />} aria-label={`Xoá câu ${index + 1}`} />
      </div>
      {question.options.map((option, optionIndex) => (
        <div key={optionIndex} className="flex items-center gap-2 pl-4">
          <input
            type="radio"
            name={`correct-${index}`}
            aria-label={`Đáp án đúng là phương án ${optionIndex + 1}`}
            checked={question.correctOptionIndex === optionIndex}
            onChange={() => form.updateQuestion(index, { correctOptionIndex: optionIndex })}
          />
          <GlassInput
            aria-label={`Phương án ${optionIndex + 1} của câu ${index + 1}`}
            placeholder={`Phương án ${optionIndex + 1}`}
            value={option}
            onChange={(event) => form.changeOption(index, optionIndex, event.target.value)}
          />
          <GlassButton variant="ghost" size="sm" onClick={() => form.removeOption(index, optionIndex)}
            disabled={question.options.length <= ASSESSMENT_LIMITS.minOptions} icon={<Trash2 size={14} aria-hidden />}
            aria-label={`Xoá phương án ${optionIndex + 1}`} />
        </div>
      ))}
      <div className="pl-4">
        <GlassInput
          aria-label={`Lời giải của câu ${index + 1}`}
          placeholder="Lời giải / giải thích (tuỳ chọn, hiện sau khi học viên nộp bài)"
          value={question.explanation}
          onChange={(event) => form.updateQuestion(index, { explanation: event.target.value })}
        />
      </div>
      <div className="pl-4">
        <GlassButton variant="ghost" size="sm" onClick={() => form.addOption(index)}
          disabled={question.options.length >= ASSESSMENT_LIMITS.maxOptions} icon={<Plus size={14} aria-hidden />}>
          Thêm phương án
        </GlassButton>
      </div>
    </fieldset>
  );
}
