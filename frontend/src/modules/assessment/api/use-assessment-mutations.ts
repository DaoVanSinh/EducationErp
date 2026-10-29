import {
  assessmentApi,
  assessmentKeys,
  type CreateAssignmentPayload,
  type CreateMiniTaskPayload,
  type ResultEntryPayload,
  type UpdateAssignmentPayload,
  type UpdateMiniTaskPayload,
} from "@/entities/assessment";
import { useMutation, useQueryClient } from "@tanstack/react-query";

/** Mọi thao tác ghi đều tải lại đúng bảng của lớp: thống kê (đã nộp, trung bình, % hoàn thành) do backend tính. */
function useInvalidate(queryKey: readonly unknown[]) {
  const queryClient = useQueryClient();
  return async () => {
    await queryClient.invalidateQueries({ queryKey });
  };
}

export function useCreateAssignment(classId: string) {
  const invalidate = useInvalidate(assessmentKeys.classAssignments(classId));
  return useMutation({
    mutationFn: (payload: CreateAssignmentPayload) => assessmentApi.createAssignment(payload),
    onSuccess: invalidate,
  });
}

export function useUpdateAssignment(classId: string) {
  const invalidate = useInvalidate(assessmentKeys.classAssignments(classId));
  return useMutation({
    mutationFn: ({ assignmentId, payload }: { assignmentId: string; payload: UpdateAssignmentPayload }) =>
      assessmentApi.updateAssignment(assignmentId, payload),
    onSuccess: invalidate,
  });
}

export function useRecordResults(classId: string) {
  const invalidate = useInvalidate(assessmentKeys.classAssignments(classId));
  return useMutation({
    mutationFn: ({ assignmentId, entries }: { assignmentId: string; entries: readonly ResultEntryPayload[] }) =>
      assessmentApi.recordResults(assignmentId, entries),
    onSuccess: invalidate,
  });
}

export function useCreateMiniTask(classId: string) {
  const invalidate = useInvalidate(assessmentKeys.classMiniTasks(classId));
  return useMutation({
    mutationFn: (payload: CreateMiniTaskPayload) => assessmentApi.createMiniTask(payload),
    onSuccess: invalidate,
  });
}

export function useUpdateMiniTask(classId: string) {
  const invalidate = useInvalidate(assessmentKeys.classMiniTasks(classId));
  return useMutation({
    mutationFn: ({ miniTaskId, payload }: { miniTaskId: string; payload: UpdateMiniTaskPayload }) =>
      assessmentApi.updateMiniTask(miniTaskId, payload),
    onSuccess: invalidate,
  });
}
