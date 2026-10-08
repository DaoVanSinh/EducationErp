import { classApi } from "@/entities/class/api/class-api";
import { classKeys } from "@/entities/class/api/class-keys";
import { QUERY_RETRY_COUNT, QUERY_STALE_TIME_MS } from "@/shared/constants/query-config";
import { keepPreviousData, useQuery } from "@tanstack/react-query";

export function useClasses(page: number, size: number) {
  return useQuery({
    queryKey: classKeys.list(page, size),
    staleTime: QUERY_STALE_TIME_MS.list,
    retry: QUERY_RETRY_COUNT,
    placeholderData: keepPreviousData,
    queryFn: () => classApi.listClasses(page, size),
  });
}
