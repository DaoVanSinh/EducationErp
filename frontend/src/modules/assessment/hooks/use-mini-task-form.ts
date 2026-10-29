import { ASSESSMENT_LIMITS } from "@/entities/assessment";
import { useCreateMiniTask, useUpdateMiniTask } from "@/modules/assessment/api/use-assessment-mutations";
import type { MiniTaskFormTarget } from "@/modules/assessment/hooks/use-class-mini-tasks-controller";
import { EMPTY_QUESTION, miniTaskFormError, type QuestionDraft } from "@/modules/assessment/model/assessment-rules";
import { useCallback, useState, type FormEvent } from "react";

function initialQuestions(target: MiniTaskFormTarget): readonly QuestionDraft[] {
  if (target === "new") {
    return [EMPTY_QUESTION];
  }
  return target.questions.map((question) => ({
    content: question.content,
    options: question.options,
    correctOptionIndex: question.correctOptionIndex ?? 0,
  }));
}

function replaceAt<T>(items: readonly T[], index: number, next: T): readonly T[] {
  return items.map((item, position) => (position === index ? next : item));
}

/** Toàn bộ state của form soạn mini-task: câu hỏi, phương án, đáp án đúng (Mandate #2). */
export function useMiniTaskForm(classId: string, target: MiniTaskFormTarget, onDone: () => void) {
  const createMiniTask = useCreateMiniTask(classId);
  const updateMiniTask = useUpdateMiniTask(classId);
  const [title, setTitle] = useState(target === "new" ? "" : target.title);
  const [description, setDescription] = useState(target === "new" ? "" : (target.description ?? ""));
  const [active, setActive] = useState(target === "new" ? true : target.active);
  const [questions, setQuestions] = useState<readonly QuestionDraft[]>(() => initialQuestions(target));
  const [validationError, setValidationError] = useState<string | null>(null);

  const updateQuestion = useCallback((index: number, patch: Partial<QuestionDraft>) => {
    setQuestions((previous) => {
      const current = previous[index];
      return current ? replaceAt(previous, index, { ...current, ...patch }) : previous;
    });
  }, []);

  const addQuestion = useCallback(() => {
    setQuestions((previous) => (previous.length >= ASSESSMENT_LIMITS.maxQuestions ? previous : [...previous, EMPTY_QUESTION]));
  }, []);

  const removeQuestion = useCallback((index: number) => {
    setQuestions((previous) => (previous.length <= 1 ? previous : previous.filter((_, position) => position !== index)));
  }, []);

  const changeOption = useCallback((index: number, optionIndex: number, value: string) => {
    setQuestions((previous) => {
      const current = previous[index];
      return current ? replaceAt(previous, index, { ...current, options: replaceAt(current.options, optionIndex, value) }) : previous;
    });
  }, []);

  const addOption = useCallback((index: number) => {
    setQuestions((previous) => {
      const current = previous[index];
      if (!current || current.options.length >= ASSESSMENT_LIMITS.maxOptions) {
        return previous;
      }
      return replaceAt(previous, index, { ...current, options: [...current.options, ""] });
    });
  }, []);

  const removeOption = useCallback((index: number, optionIndex: number) => {
    setQuestions((previous) => {
      const current = previous[index];
      if (!current || current.options.length <= ASSESSMENT_LIMITS.minOptions) {
        return previous;
      }
      const options = current.options.filter((_, position) => position !== optionIndex);
      const correct = current.correctOptionIndex >= options.length ? 0 : current.correctOptionIndex;
      return replaceAt(previous, index, { ...current, options, correctOptionIndex: correct });
    });
  }, []);

  const handleSubmit = useCallback(
    (event: FormEvent<HTMLFormElement>) => {
      event.preventDefault();
      const error = miniTaskFormError(title, questions);
      setValidationError(error);
      if (error !== null) {
        return;
      }
      const payload = {
        title: title.trim(),
        description: description.trim() === "" ? null : description.trim(),
        questions: questions.map((question) => ({
          content: question.content.trim(),
          options: question.options.map((option) => option.trim()),
          correctOptionIndex: question.correctOptionIndex,
        })),
      };
      const options = { onSuccess: onDone };
      if (target === "new") {
        createMiniTask.mutate({ ...payload, classId }, options);
      } else {
        updateMiniTask.mutate({ miniTaskId: target.id, payload: { ...payload, active } }, options);
      }
    },
    [active, classId, createMiniTask, description, onDone, questions, target, title, updateMiniTask],
  );

  return {
    isNew: target === "new",
    title, setTitle, description, setDescription, active, setActive,
    questions, updateQuestion, addQuestion, removeQuestion, changeOption, addOption, removeOption,
    handleSubmit,
    validationError,
    submitError: createMiniTask.error ?? updateMiniTask.error,
    isSubmitting: createMiniTask.isPending || updateMiniTask.isPending,
  };
}

export type MiniTaskForm = ReturnType<typeof useMiniTaskForm>;
