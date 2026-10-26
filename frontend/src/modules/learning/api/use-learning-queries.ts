import { learningApi } from "@/modules/learning/api/learning-api";
import { learningKeys } from "@/modules/learning/api/learning-keys";
import { QUERY_RETRY_COUNT, QUERY_STALE_TIME_MS } from "@/shared/constants/query-config";
import { useQuery } from "@tanstack/react-query";

export function useMyLearningClasses() {
  return useQuery({
    queryKey: learningKeys.myClasses(),
    staleTime: QUERY_STALE_TIME_MS.list,
    retry: QUERY_RETRY_COUNT,
    queryFn: () => learningApi.listMyClasses(),
  });
}

export function useMyInvoices() {
  return useQuery({
    queryKey: learningKeys.myInvoices(),
    staleTime: QUERY_STALE_TIME_MS.list,
    retry: QUERY_RETRY_COUNT,
    queryFn: () => learningApi.listMyInvoices(),
  });
}
