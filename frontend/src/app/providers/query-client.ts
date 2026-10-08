import { ApiError } from "@/shared/api/api-error";
import { QUERY_RETRY_COUNT } from "@/shared/constants/query-config";
import { QueryClient } from "@tanstack/react-query";

/**
 * Cấu hình cache dùng chung. Lỗi 4xx không thử lại: dữ liệu sai hoặc thiếu quyền thì gọi thêm hai lần
 * nữa cũng vẫn thế, chỉ làm người dùng chờ lâu hơn trước khi thấy thông báo.
 */
export function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        refetchOnWindowFocus: false,
        retry: (failureCount, error) => {
          if (error instanceof ApiError && error.status >= 400 && error.status < 500) {
            return false;
          }
          return failureCount < QUERY_RETRY_COUNT;
        },
      },
      mutations: { retry: false },
    },
  });
}
