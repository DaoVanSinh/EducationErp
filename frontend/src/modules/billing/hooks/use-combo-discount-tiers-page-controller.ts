import { useComboDiscountTiers, type ComboDiscountTier } from "@/entities/billing";
import { useUpdateComboDiscountTier } from "@/modules/billing/api/use-combo-discount-tier-mutations";
import { useCallback, useState } from "react";

/** Toàn bộ state/query/mutation của trang bậc giảm giá - ui/* chỉ render (Mandate #2). */
export function useComboDiscountTiersPageController() {
  const [createDialogOpen, setCreateDialogOpen] = useState(false);
  const tiers = useComboDiscountTiers();
  const updateTier = useUpdateComboDiscountTier();

  const openCreateDialog = useCallback(() => setCreateDialogOpen(true), []);
  const closeCreateDialog = useCallback(() => setCreateDialogOpen(false), []);

  /** "Xoá" một bậc là tắt active - hệ thống không có endpoint DELETE nào cho bảng này. */
  const onToggleActive = useCallback(
    (tier: ComboDiscountTier) => {
      void updateTier.mutateAsync({
        tierId: tier.id,
        payload: { discountPercent: tier.discountPercent, active: !tier.active },
      });
    },
    [updateTier],
  );

  return {
    tiers,
    createDialogOpen,
    openCreateDialog,
    closeCreateDialog,
    onToggleActive,
    isMutating: updateTier.isPending,
    mutationError: updateTier.error,
  };
}
