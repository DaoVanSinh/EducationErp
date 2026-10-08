import { studentApi } from "@/entities/student/api/student-api";
import { studentKeys } from "@/entities/student/api/student-keys";
import { QUERY_RETRY_COUNT, QUERY_STALE_TIME_MS } from "@/shared/constants/query-config";
import { keepPreviousData, useQuery } from "@tanstack/react-query";

export function useStudents(page: number, size: number) {
  return useQuery({
    queryKey: studentKeys.list(page, size),
    staleTime: QUERY_STALE_TIME_MS.list,
    retry: QUERY_RETRY_COUNT,
    placeholderData: keepPreviousData,
    queryFn: () => studentApi.listProfiles(page, size),
  });
}
