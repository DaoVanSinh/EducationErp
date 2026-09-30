import { branchApi } from "@/entities/branch/api/branch-api";
import { branchKeys } from "@/entities/branch/api/branch-keys";
import { QUERY_RETRY_COUNT, QUERY_STALE_TIME_MS } from "@/shared/constants/query-config";
import { keepPreviousData, useQuery } from "@tanstack/react-query";

/**
 * Một trang danh sách chi nhánh. placeholderData giữ lại trang trước trong lúc tải trang sau, để bảng
 * không sập xuống thành khung trắng mỗi lần bấm sang trang.
 */
export function useBranches(page: number, size: number) {
  return useQuery({
    queryKey: branchKeys.list(page, size),
    staleTime: QUERY_STALE_TIME_MS.list,
    retry: QUERY_RETRY_COUNT,
    placeholderData: keepPreviousData,
    queryFn: ({ signal }) => branchApi.listBranches(page, size, signal),
  });
}
