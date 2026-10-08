import { academicApi } from "@/entities/academic/api/academic-api";
import { academicKeys } from "@/entities/academic/api/academic-keys";
import { QUERY_RETRY_COUNT, QUERY_STALE_TIME_MS } from "@/shared/constants/query-config";
import { useQuery } from "@tanstack/react-query";

/** Tiến trình được TÍNH lúc đọc ở backend (không lưu), nên staleTime ngắn như mọi danh sách khác là
 * đúng: mỗi lần làm mới là một con số mới theo thời gian thực. */
export function useClassProgress(classId: string) {
  return useQuery({
    queryKey: academicKeys.progress(classId),
    staleTime: QUERY_STALE_TIME_MS.list,
    retry: QUERY_RETRY_COUNT,
    queryFn: () => academicApi.getClassProgress(classId),
  });
}
