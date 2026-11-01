import { usePracticeMiniTask, type AttemptResult } from "@/entities/assessment";
import { useSubmitAttempt } from "@/modules/learning/api/use-practice-mutation";
import { newPaperSeed, orderPaper, reviewsByQuestionId } from "@/modules/learning/model/practice-order";
import { useCallback, useMemo, useState } from "react";

type Answers = Readonly<Record<string, number>>;

/**
 * State của trang làm mini-task: thứ tự câu/phương án đã xáo cho lượt này, lựa chọn từng câu, nộp, kết quả,
 * làm lại (xáo lại thứ tự) - Mandate #2.
 */
export function usePracticeMiniTaskController(miniTaskId: string) {
  const paper = usePracticeMiniTask(miniTaskId);
  const submitAttempt = useSubmitAttempt(miniTaskId);
  const [seed, setSeed] = useState(newPaperSeed);
  const [answers, setAnswers] = useState<Answers>({});
  const [result, setResult] = useState<AttemptResult | null>(null);

  const orderedQuestions = useMemo(() => orderPaper(paper.data?.questions ?? [], seed), [paper.data, seed]);
  const reviews = useMemo(() => reviewsByQuestionId(result), [result]);

  const choose = useCallback((questionId: string, optionIndex: number) => {
    setAnswers((previous) => ({ ...previous, [questionId]: optionIndex }));
  }, []);

  const submit = useCallback(() => {
    // Gửi theo thứ tự câu GỐC - thứ tự xáo chỉ để hiển thị.
    const questions = paper.data?.questions ?? [];
    const ordered = questions.map((question) => answers[question.id] ?? null);
    submitAttempt.mutate(ordered, { onSuccess: setResult });
  }, [answers, paper.data, submitAttempt]);

  const retry = useCallback(() => {
    setAnswers({});
    setResult(null);
    setSeed(newPaperSeed());
    submitAttempt.reset();
  }, [submitAttempt]);

  const questionCount = paper.data?.questions.length ?? 0;

  return {
    paper,
    orderedQuestions,
    answers,
    result,
    reviews,
    choose,
    submit,
    retry,
    answeredCount: Object.keys(answers).length,
    questionCount,
    isSubmitting: submitAttempt.isPending,
    submitError: submitAttempt.error,
  };
}
