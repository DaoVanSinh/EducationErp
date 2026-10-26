import { classApi } from "@/entities/class/api/class-api";
import { classKeys } from "@/entities/class/api/class-keys";
import { QUERY_RETRY_COUNT, QUERY_STALE_TIME_MS } from "@/shared/constants/query-config";
import { keepPreviousData, useQuery } from "@tanstack/react-query";

/** Lớp mà giáo viên đang đăng nhập được phân công dạy (cổng giảng dạy). */
export function useMyTeachingClasses(page: number, size: number) {
  return useQuery({
    queryKey: classKeys.mine(page, size),
    staleTime: QUERY_STALE_TIME_MS.list,
    retry: QUERY_RETRY_COUNT,
    placeholderData: keepPreviousData,
    queryFn: () => classApi.listMyTeachingClasses(page, size),
  });
}
