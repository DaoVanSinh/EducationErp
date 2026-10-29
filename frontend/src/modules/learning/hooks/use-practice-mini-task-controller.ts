import { usePracticeMiniTask, type AttemptResult } from "@/entities/assessment";
import { useSubmitAttempt } from "@/modules/learning/api/use-practice-mutation";
import { useCallback, useState } from "react";

type Answers = Readonly<Record<string, number>>;

/** State của trang làm mini-task: lựa chọn từng câu, nộp, kết quả, làm lại (Mandate #2). */
export function usePracticeMiniTaskController(miniTaskId: string) {
  const paper = usePracticeMiniTask(miniTaskId);
  const submitAttempt = useSubmitAttempt(miniTaskId);
  const [answers, setAnswers] = useState<Answers>({});
  const [result, setResult] = useState<AttemptResult | null>(null);

  const choose = useCallback((questionId: string, optionIndex: number) => {
    setAnswers((previous) => ({ ...previous, [questionId]: optionIndex }));
  }, []);

  const submit = useCallback(() => {
    const questions = paper.data?.questions ?? [];
    const ordered = questions.map((question) => answers[question.id] ?? null);
    submitAttempt.mutate(ordered, { onSuccess: setResult });
  }, [answers, paper.data, submitAttempt]);

  const retry = useCallback(() => {
    setAnswers({});
    setResult(null);
    submitAttempt.reset();
  }, [submitAttempt]);

  const questionCount = paper.data?.questions.length ?? 0;

  return {
    paper,
    answers,
    result,
    choose,
    submit,
    retry,
    answeredCount: Object.keys(answers).length,
    questionCount,
    isSubmitting: submitAttempt.isPending,
    submitError: submitAttempt.error,
  };
}
