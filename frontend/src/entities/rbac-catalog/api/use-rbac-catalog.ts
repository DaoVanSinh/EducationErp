import { catalogApi } from "@/entities/rbac-catalog/api/catalog-api";
import { catalogKeys } from "@/entities/rbac-catalog/api/catalog-keys";
import { QUERY_STALE_TIME_MS } from "@/shared/constants/query-config";
import { useQuery } from "@tanstack/react-query";

export interface RbacCatalogOptions {
  /**
   * Tắt khi người dùng không có quyền đọc danh mục (ROLE:READ cấp tổ chức) - gọi vẫn chỉ nhận 403, và một
   * hộp thoại dùng danh mục làm nguồn tuỳ chọn sẽ hiện lỗi đỏ vô nghĩa thay vì tự rơi về giá trị mặc định.
   */
  readonly enabled?: boolean;
}

/** Dữ liệu tham chiếu cho mọi dropdown của màn hình quản trị. Đổi rất ít nên giữ lâu trong cache. */
export function useRbacCatalog({ enabled = true }: RbacCatalogOptions = {}) {
  return useQuery({
    queryKey: catalogKeys.detail(),
    staleTime: QUERY_STALE_TIME_MS.catalog,
    enabled,
    queryFn: () => catalogApi.getCatalog(),
  });
}
