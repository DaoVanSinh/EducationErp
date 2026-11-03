import { courseApi } from "@/entities/course/api/course-api";
import { courseKeys } from "@/entities/course/api/course-keys";
import { QUERY_RETRY_COUNT, QUERY_STALE_TIME_MS } from "@/shared/constants/query-config";
import { keepPreviousData, useQuery } from "@tanstack/react-query";

export function useCourses(page: number, size: number, branchId?: string) {
  return useQuery({
    queryKey: courseKeys.list(page, size, branchId),
    staleTime: QUERY_STALE_TIME_MS.list,
    retry: QUERY_RETRY_COUNT,
    placeholderData: keepPreviousData,
    queryFn: () => courseApi.listCourses(page, size, branchId),
  });
}
