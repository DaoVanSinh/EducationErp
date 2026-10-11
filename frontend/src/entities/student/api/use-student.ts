import { studentApi } from "@/entities/student/api/student-api";
import { studentKeys } from "@/entities/student/api/student-keys";
import { QUERY_RETRY_COUNT, QUERY_STALE_TIME_MS } from "@/shared/constants/query-config";
import { useQuery } from "@tanstack/react-query";

export function useStudent(profileId: string) {
  return useQuery({
    queryKey: studentKeys.detail(profileId),
    staleTime: QUERY_STALE_TIME_MS.list,
    retry: QUERY_RETRY_COUNT,
    queryFn: () => studentApi.getProfile(profileId),
  });
}
