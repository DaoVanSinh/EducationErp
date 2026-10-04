import type { ComboDiscountTier } from "@/entities/billing";
import { Can } from "@/entities/permission";
import { ACCESS_RULE } from "@/shared/constants/permissions";
import { Badge } from "@/shared/ui/badge";
import { GlassButton } from "@/shared/ui/glass-button";
import { Power } from "lucide-react";

export interface ComboDiscountTiersTableProps {
  readonly rows: readonly ComboDiscountTier[];
  readonly onToggleActive: (tier: ComboDiscountTier) => void;
  readonly isMutating: boolean;
}

/** Không có nút xoá: tắt active là cách "xoá" một bậc (mirror Course.active). */
export function ComboDiscountTiersTable({ rows, onToggleActive, isMutating }: ComboDiscountTiersTableProps) {
  return (
    <ul className="flex flex-col gap-2">
      {rows.map((tier) => (
        <li
          key={tier.id}
          className="glass grid grid-cols-1 items-center gap-3 rounded-2xl p-3 lg:grid-cols-[minmax(0,1fr)_auto_auto_auto]"
        >
          <p className="truncate text-sm text-mist-100">Từ {tier.minCourseCount} khoá trở lên</p>
          <p className="text-sm text-mist-100">giảm {tier.discountPercent}%</p>
          <Badge tone={tier.active ? "positive" : "neutral"}>
            {tier.active ? "Đang áp dụng" : "Đã tắt"}
          </Badge>
          <Can {...ACCESS_RULE.updateInvoice}>
            <GlassButton
              variant="ghost"
              size="sm"
              disabled={isMutating}
              onClick={() => onToggleActive(tier)}
              icon={<Power size={14} aria-hidden />}
            >
              {tier.active ? "Tắt bậc này" : "Bật lại"}
            </GlassButton>
          </Can>
        </li>
      ))}
    </ul>
  );
}
