import { assessmentApi } from "@/entities/assessment/api/assessment-api";
import { assessmentKeys } from "@/entities/assessment/api/assessment-keys";
import { QUERY_RETRY_COUNT, QUERY_STALE_TIME_MS } from "@/shared/constants/query-config";
import { useQuery } from "@tanstack/react-query";

export function useClassAssignments(classId: string) {
  return useQuery({
    queryKey: assessmentKeys.classAssignments(classId),
    staleTime: QUERY_STALE_TIME_MS.list,
    retry: QUERY_RETRY_COUNT,
    queryFn: () => assessmentApi.getClassAssignments(classId),
  });
}

export function useClassMiniTasks(classId: string) {
  return useQuery({
    queryKey: assessmentKeys.classMiniTasks(classId),
    staleTime: QUERY_STALE_TIME_MS.list,
    retry: QUERY_RETRY_COUNT,
    queryFn: () => assessmentApi.getClassMiniTasks(classId),
  });
}

export function useMyAssessments() {
  return useQuery({
    queryKey: assessmentKeys.mine(),
    staleTime: QUERY_STALE_TIME_MS.list,
    retry: QUERY_RETRY_COUNT,
    queryFn: () => assessmentApi.listMyAssessments(),
  });
}

/** Đề không đổi trong lúc làm bài - không tự làm mới giữa chừng làm mất lựa chọn của học viên. */
export function usePracticeMiniTask(miniTaskId: string) {
  return useQuery({
    queryKey: assessmentKeys.practice(miniTaskId),
    staleTime: QUERY_STALE_TIME_MS.catalog,
    retry: QUERY_RETRY_COUNT,
    queryFn: () => assessmentApi.getPracticeMiniTask(miniTaskId),
  });
}
