import { parseQuestionText, QUESTION_IMPORT_SAMPLE } from "@/modules/assessment/model/question-import";
import type { QuestionDraft } from "@/modules/assessment/model/assessment-rules";
import { useCallback, useState } from "react";

/**
 * Ô "Nhập nhanh" của form mini-task: dán cả bộ câu hỏi dạng văn bản thay vì gõ từng ô (Mandate #2).
 * Câu hợp lệ được thêm vào form, câu lỗi giữ lại kèm thông báo để sửa.
 */
export function useQuestionImport(onImport: (questions: readonly QuestionDraft[]) => void) {
  const [open, setOpen] = useState(false);
  const [text, setText] = useState("");
  const [errors, setErrors] = useState<readonly string[]>([]);
  const [importedCount, setImportedCount] = useState<number | null>(null);

  const toggle = useCallback(() => setOpen((previous) => !previous), []);
  const fillSample = useCallback(() => setText(QUESTION_IMPORT_SAMPLE), []);

  const runImport = useCallback(() => {
    const result = parseQuestionText(text);
    setErrors(result.errors);
    setImportedCount(result.questions.length);
    if (result.questions.length > 0) {
      onImport(result.questions);
    }
    if (result.errors.length === 0) {
      setText("");
    }
  }, [onImport, text]);

  return { open, toggle, text, setText, fillSample, runImport, errors, importedCount };
}
