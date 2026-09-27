import { rbacCatalogSchema } from "@/entities/rbac-catalog/model/catalog-schema";
import { apiClient } from "@/shared/api/api-client";
import { API_ROUTE } from "@/shared/constants/api-routes";

export const catalogApi = {
  async getCatalog(signal?: AbortSignal) {
    return rbacCatalogSchema.parse(await apiClient.get<unknown>(API_ROUTE.rbac.catalog, undefined, signal));
  },
} as const;
