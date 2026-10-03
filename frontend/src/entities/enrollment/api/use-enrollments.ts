import { enrollmentApi } from "@/entities/enrollment/api/enrollment-api";
import { enrollmentKeys } from "@/entities/enrollment/api/enrollment-keys";
import { QUERY_RETRY_COUNT, QUERY_STALE_TIME_MS } from "@/shared/constants/query-config";
import { keepPreviousData, useQuery } from "@tanstack/react-query";

export function useEnrollments(page: number, size: number, studentProfileId?: string, classId?: string) {
  return useQuery({
    queryKey: enrollmentKeys.list(page, size, studentProfileId, classId),
    staleTime: QUERY_STALE_TIME_MS.list,
    retry: QUERY_RETRY_COUNT,
    placeholderData: keepPreviousData,
    queryFn: () => enrollmentApi.listEnrollments(page, size, studentProfileId, classId),
  });
}
