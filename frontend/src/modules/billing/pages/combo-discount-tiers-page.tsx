import { Can, RequirePermission } from "@/entities/permission";
import { useComboDiscountTiersPageController } from "@/modules/billing/hooks/use-combo-discount-tiers-page-controller";
import { ComboDiscountTierDialog } from "@/modules/billing/ui/combo-discount-tier-dialog";
import { ComboDiscountTiersTable } from "@/modules/billing/ui/combo-discount-tiers-table";
import { ACCESS_RULE } from "@/shared/constants/permissions";
import { EmptyState } from "@/shared/ui/empty-state";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassPanel } from "@/shared/ui/glass-panel";
import { PageHeader } from "@/shared/ui/page-header";
import { Skeleton } from "@/shared/ui/skeleton";
import { Percent, Plus } from "lucide-react";

export function ComboDiscountTiersPage() {
  const controller = useComboDiscountTiersPageController();

  return (
    <RequirePermission {...ACCESS_RULE.readInvoice}>
      <div className="flex flex-col gap-6">
        <PageHeader
          title="Bậc giảm giá combo"
          description="Số khoá tối thiểu → % giảm. Chưa có bậc nào phù hợp thì hệ thống từ chối tạo combo, không tự giảm 0%."
          actions={
            <Can {...ACCESS_RULE.createInvoice}>
              <GlassButton onClick={controller.openCreateDialog} icon={<Plus size={16} aria-hidden />}>
                Thêm bậc
              </GlassButton>
            </Can>
          }
        />

        {controller.tiers.isError ? <ErrorNotice error={controller.tiers.error} /> : null}
        {controller.mutationError ? <ErrorNotice error={controller.mutationError} /> : null}

        <GlassPanel className="flex flex-col gap-4">
          {controller.tiers.isPending ? <Skeleton className="h-16" /> : null}
          {controller.tiers.data ? <TiersListSection controller={controller} /> : null}
        </GlassPanel>

        <ComboDiscountTierDialog
          open={controller.createDialogOpen}
          onClose={controller.closeCreateDialog}
        />
      </div>
    </RequirePermission>
  );
}

/** Tách nhánh rỗng/có dữ liệu ra component riêng - tránh nested ternary trong JSX (Mandate #3). */
function TiersListSection({
  controller,
}: {
  readonly controller: ReturnType<typeof useComboDiscountTiersPageController>;
}) {
  const tiers = controller.tiers.data ?? [];
  if (tiers.length === 0) {
    return (
      <EmptyState
        icon={<Percent size={28} aria-hidden />}
        title="Chưa cấu hình bậc giảm giá nào"
        description="Thêm bậc đầu tiên (ví dụ: từ 2 khoá giảm 10%) để kế toán tạo được combo."
      />
    );
  }
  return (
    <ComboDiscountTiersTable
      rows={tiers}
      onToggleActive={controller.onToggleActive}
      isMutating={controller.isMutating}
    />
  );
}
