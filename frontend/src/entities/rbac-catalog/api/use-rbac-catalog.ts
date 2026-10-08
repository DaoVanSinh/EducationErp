import { catalogApi } from "@/entities/rbac-catalog/api/catalog-api";
import { catalogKeys } from "@/entities/rbac-catalog/api/catalog-keys";
import { QUERY_STALE_TIME_MS } from "@/shared/constants/query-config";
import { useQuery } from "@tanstack/react-query";

/** Dữ liệu tham chiếu cho mọi dropdown của màn hình quản trị. Đổi rất ít nên giữ lâu trong cache. */
export function useRbacCatalog() {
  return useQuery({
    queryKey: catalogKeys.detail(),
    staleTime: QUERY_STALE_TIME_MS.catalog,
    queryFn: () => catalogApi.getCatalog(),
  });
}
