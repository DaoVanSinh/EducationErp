import { attendanceApi } from "@/entities/attendance/api/attendance-api";
import { attendanceKeys } from "@/entities/attendance/api/attendance-keys";
import { QUERY_RETRY_COUNT, QUERY_STALE_TIME_MS } from "@/shared/constants/query-config";
import { useQuery } from "@tanstack/react-query";

export function useClassAttendance(classId: string) {
  return useQuery({
    queryKey: attendanceKeys.class(classId),
    staleTime: QUERY_STALE_TIME_MS.list,
    retry: QUERY_RETRY_COUNT,
    queryFn: () => attendanceApi.getClassAttendance(classId),
  });
}

export function useMyAttendance() {
  return useQuery({
    queryKey: attendanceKeys.mine(),
    staleTime: QUERY_STALE_TIME_MS.list,
    retry: QUERY_RETRY_COUNT,
    queryFn: () => attendanceApi.listMyAttendance(),
  });
}
