import {
  billingApi,
  billingKeys,
  type CreateComboDiscountTierPayload,
  type UpdateComboDiscountTierPayload,
} from "@/entities/billing";
import { useMutation, useQueryClient } from "@tanstack/react-query";

/** Đổi bậc giảm giá không ảnh hưởng combo đã tạo (Combo.discountPercent là snapshot), nên chỉ cần
 * dọn đúng cache danh sách bậc - không invalidate cả billingKeys.all như các mutation hoá đơn. */
function useDiscountTiersInvalidation(): () => Promise<void> {
  const queryClient = useQueryClient();
  return async () => {
    await queryClient.invalidateQueries({ queryKey: billingKeys.discountTiers() });
  };
}

export function useCreateComboDiscountTier() {
  const invalidate = useDiscountTiersInvalidation();
  return useMutation({
    mutationFn: (payload: CreateComboDiscountTierPayload) => billingApi.createComboDiscountTier(payload),
    onSuccess: invalidate,
  });
}

export function useUpdateComboDiscountTier() {
  const invalidate = useDiscountTiersInvalidation();
  return useMutation({
    mutationFn: ({ tierId, payload }: { tierId: string; payload: UpdateComboDiscountTierPayload }) =>
      billingApi.updateComboDiscountTier(tierId, payload),
    onSuccess: invalidate,
  });
}
