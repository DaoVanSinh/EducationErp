import { teacherApi } from "@/entities/teacher/api/teacher-api";
import { teacherKeys } from "@/entities/teacher/api/teacher-keys";
import { QUERY_RETRY_COUNT, QUERY_STALE_TIME_MS } from "@/shared/constants/query-config";
import { keepPreviousData, useQuery } from "@tanstack/react-query";

export function useTeachers(page: number, size: number) {
  return useQuery({
    queryKey: teacherKeys.list(page, size),
    staleTime: QUERY_STALE_TIME_MS.list,
    retry: QUERY_RETRY_COUNT,
    placeholderData: keepPreviousData,
    queryFn: () => teacherApi.listProfiles(page, size),
  });
}
