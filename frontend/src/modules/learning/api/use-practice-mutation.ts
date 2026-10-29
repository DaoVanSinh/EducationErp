import { assessmentApi, assessmentKeys } from "@/entities/assessment";
import { useMutation, useQueryClient } from "@tanstack/react-query";

/** Nộp một lượt làm mini-task; xong thì làm mới trang "Bài tập & luyện tập" (điểm cao nhất, số lượt). */
export function useSubmitAttempt(miniTaskId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (answers: readonly (number | null)[]) => assessmentApi.submitAttempt(miniTaskId, answers),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: assessmentKeys.mine() });
    },
  });
}
