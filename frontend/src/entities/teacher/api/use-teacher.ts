import { teacherApi } from "@/entities/teacher/api/teacher-api";
import { teacherKeys } from "@/entities/teacher/api/teacher-keys";
import { QUERY_RETRY_COUNT, QUERY_STALE_TIME_MS } from "@/shared/constants/query-config";
import { useQuery } from "@tanstack/react-query";

export function useTeacher(profileId: string) {
  return useQuery({
    queryKey: teacherKeys.detail(profileId),
    staleTime: QUERY_STALE_TIME_MS.list,
    retry: QUERY_RETRY_COUNT,
    queryFn: () => teacherApi.getProfile(profileId),
  });
}
