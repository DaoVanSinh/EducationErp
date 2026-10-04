import { billingApi } from "@/entities/billing/api/billing-api";
import { billingKeys } from "@/entities/billing/api/billing-keys";
import { QUERY_RETRY_COUNT, QUERY_STALE_TIME_MS } from "@/shared/constants/query-config";
import { useQuery } from "@tanstack/react-query";

/** Bậc giảm giá đổi rất ít nên dùng staleTime của danh sách; dialog tạo combo đọc nó để xem trước
 * % giảm, còn con số CHỐT vẫn do backend tính lúc tạo (CreateCombo). */
export function useComboDiscountTiers() {
  return useQuery({
    queryKey: billingKeys.discountTiers(),
    staleTime: QUERY_STALE_TIME_MS.list,
    retry: QUERY_RETRY_COUNT,
    queryFn: () => billingApi.listComboDiscountTiers(),
  });
}
