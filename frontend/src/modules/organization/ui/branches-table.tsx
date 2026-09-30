import type { BranchSummary } from "@/entities/branch";
import { Can } from "@/entities/permission";
import { ACCESS_RULE } from "@/shared/constants/permissions";
import { staggerDelay } from "@/shared/lib/motion";
import { Badge } from "@/shared/ui/badge";
import { GlassButton } from "@/shared/ui/glass-button";
import { m } from "framer-motion";
import { Pencil } from "lucide-react";

export interface BranchesTableProps {
  readonly rows: readonly BranchSummary[];
  readonly onEdit: (branch: BranchSummary) => void;
}

export function BranchesTable({ rows, onEdit }: BranchesTableProps) {
  return (
    <ul className="flex flex-col gap-2">
      {rows.map((branch, index) => (
        <m.li
          key={branch.id}
          initial={{ opacity: 0, y: 8 }}
          animate={{ opacity: 1, y: 0 }}
          transition={staggerDelay(index)}
          className="glass grid grid-cols-1 items-center gap-3 rounded-2xl p-3 lg:grid-cols-[minmax(0,1fr)_minmax(0,2fr)_auto_auto]"
        >
          <p className="truncate text-sm text-mist-100">{branch.code}</p>

          <div className="min-w-0">
            <p className="truncate text-sm text-mist-100">{branch.name}</p>
            <p className="truncate text-xs text-mist-500">{branch.address ?? "Chưa có địa chỉ"}</p>
          </div>

          <Badge tone={branch.active ? "positive" : "neutral"}>
            {branch.active ? "Đang hoạt động" : "Đã vô hiệu hoá"}
          </Badge>

          <Can {...ACCESS_RULE.updateBranch}>
            <div className="justify-self-start lg:justify-self-end">
              <GlassButton
                variant="secondary"
                size="sm"
                onClick={() => onEdit(branch)}
                icon={<Pencil size={14} aria-hidden />}
              >
                Sửa
              </GlassButton>
            </div>
          </Can>
        </m.li>
      ))}
    </ul>
  );
}
