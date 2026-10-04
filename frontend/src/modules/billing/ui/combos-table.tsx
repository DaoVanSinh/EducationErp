import type { Combo } from "@/entities/billing";
import { buildComboDetailPath } from "@/shared/constants/app-routes";
import { formatter } from "@/shared/lib/format";
import { staggerDelay } from "@/shared/lib/motion";
import { Badge } from "@/shared/ui/badge";
import { GlassButton } from "@/shared/ui/glass-button";
import { m } from "framer-motion";
import { ChevronRight } from "lucide-react";
import { Link } from "react-router-dom";

export interface CombosTableProps {
  readonly rows: readonly Combo[];
}

export function CombosTable({ rows }: CombosTableProps) {
  return (
    <ul className="flex flex-col gap-2">
      {rows.map((combo, index) => (
        <m.li
          key={combo.id}
          initial={{ opacity: 0, y: 8 }}
          animate={{ opacity: 1, y: 0 }}
          transition={staggerDelay(index)}
          className="glass grid grid-cols-1 items-center gap-3 rounded-2xl p-3 lg:grid-cols-[minmax(0,1fr)_auto_auto_auto_auto]"
        >
          <div className="min-w-0">
            <p className="truncate text-sm text-mist-100">{combo.courseCount} khoá trong combo</p>
            <p className="truncate text-xs text-mist-500">
              HV {combo.studentProfileId.slice(0, 8)} · hạn {combo.dueDate}
            </p>
          </div>
          <p className="text-xs text-mist-500 line-through">
            {formatter.count(combo.totalOriginalAmount)} đ
          </p>
          <Badge tone="accent">-{combo.discountPercent}%</Badge>
          <p className="text-sm text-mist-100">{formatter.count(combo.totalDiscountedAmount)} đ</p>
          <Link to={buildComboDetailPath(combo.id)}>
            <GlassButton variant="secondary" size="sm" icon={<ChevronRight size={14} aria-hidden />}>
              Xem chi tiết
            </GlassButton>
          </Link>
        </m.li>
      ))}
    </ul>
  );
}
