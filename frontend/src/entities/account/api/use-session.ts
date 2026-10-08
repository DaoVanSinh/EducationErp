import { accountKeys } from "@/entities/account/api/account-keys";
import { accountApi } from "@/entities/account/api/account-api";
import type { Session } from "@/entities/account/model/account-schema";
import { ApiError } from "@/shared/api/api-error";
import { QUERY_STALE_TIME_MS } from "@/shared/constants/query-config";
import { useQuery, type UseQueryResult } from "@tanstack/react-query";

/**
 * Phiên hiện tại, hoặc null nếu chưa đăng nhập.
 *
 * 401 ở đây là câu trả lời hợp lệ ("chưa đăng nhập"), không phải sự cố - nên nó thành null chứ không
 * thành error, để trang đăng nhập không phải hiển thị một thông báo lỗi đỏ khi vừa mở lên.
 */
export function useSession(): UseQueryResult<Session | null> {
  return useQuery({
    queryKey: accountKeys.session(),
    staleTime: QUERY_STALE_TIME_MS.session,
    retry: false,
    queryFn: async () => {
      try {
        return await accountApi.getSession();
      } catch (error) {
        if (error instanceof ApiError && error.isUnauthorized) {
          return null;
        }
        throw error;
      }
    },
  });
}
