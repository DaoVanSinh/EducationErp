import { academicApi } from "@/entities/academic/api/academic-api";
import { academicKeys } from "@/entities/academic/api/academic-keys";
import { QUERY_RETRY_COUNT, QUERY_STALE_TIME_MS } from "@/shared/constants/query-config";
import { useQuery } from "@tanstack/react-query";

export function useStudentAcademicSummary(studentProfileId: string) {
  return useQuery({
    queryKey: academicKeys.studentSummary(studentProfileId),
    staleTime: QUERY_STALE_TIME_MS.list,
    retry: QUERY_RETRY_COUNT,
    queryFn: () => academicApi.getStudentSummary(studentProfileId),
  });
}
