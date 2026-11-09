import { accountApi } from "@/entities/account/api/account-api";
import { accountKeys } from "@/entities/account/api/account-keys";
import type { AccountListFilters } from "@/entities/account/model/account-schema";
import { QUERY_RETRY_COUNT, QUERY_STALE_TIME_MS } from "@/shared/constants/query-config";
import { keepPreviousData, useQuery } from "@tanstack/react-query";

/**
 * Một trang danh sách tài khoản. placeholderData giữ lại trang trước trong lúc tải trang sau, để bảng
 * không sập xuống thành khung trắng mỗi lần bấm sang trang.
 */
export function useAccounts(page: number, size: number, filters: AccountListFilters) {
  return useQuery({
    queryKey: accountKeys.list(page, size, filters),
    staleTime: QUERY_STALE_TIME_MS.list,
    retry: QUERY_RETRY_COUNT,
    placeholderData: keepPreviousData,
    queryFn: () => accountApi.listAccounts(page, size, filters),
  });
}
