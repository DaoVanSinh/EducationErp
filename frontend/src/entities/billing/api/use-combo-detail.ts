import { billingApi } from "@/entities/billing/api/billing-api";
import { billingKeys } from "@/entities/billing/api/billing-keys";
import { QUERY_RETRY_COUNT } from "@/shared/constants/query-config";
import { useQuery } from "@tanstack/react-query";

export function useComboDetail(comboId: string) {
  return useQuery({
    queryKey: billingKeys.comboDetail(comboId),
    retry: QUERY_RETRY_COUNT,
    enabled: comboId.length > 0,
    queryFn: () => billingApi.getCombo(comboId),
  });
}
