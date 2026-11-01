import { Can } from "@/entities/permission";
import type { GroupRegistrationRow } from "@/modules/billing/hooks/use-group-registrations-page-controller";
import { ACCESS_RULE } from "@/shared/constants/permissions";
import { formatter } from "@/shared/lib/format";
import { staggerDelay } from "@/shared/lib/motion";
import { Badge } from "@/shared/ui/badge";
import { GlassButton } from "@/shared/ui/glass-button";
import { m } from "framer-motion";
import { Users, XCircle } from "lucide-react";

export interface GroupRegistrationsTableProps {
  readonly rows: readonly GroupRegistrationRow[];
  readonly onCancel: (row: GroupRegistrationRow) => void;
}

/** Mỗi dòng là một nhóm: khoá học, ai trong nhóm, % giảm và học phí mỗi người phải đóng. */
export function GroupRegistrationsTable({ rows, onCancel }: GroupRegistrationsTableProps) {
  return (
    <ul className="flex flex-col gap-2">
      {rows.map((row, index) => (
        <m.li
          key={row.group.id}
          initial={{ opacity: 0, y: 8 }}
          animate={{ opacity: 1, y: 0 }}
          transition={staggerDelay(index)}
          className="glass grid grid-cols-1 items-center gap-3 rounded-2xl p-3 lg:grid-cols-[minmax(0,1fr)_auto_auto_auto]"
        >
          <div className="min-w-0">
            <p className="truncate text-sm font-semibold text-mist-100">{row.courseName}</p>
            <p className="flex items-center gap-1.5 truncate text-xs text-mist-500">
              <Users size={12} aria-hidden /> {row.memberNames.length} người: {row.memberNames.join(", ")}
            </p>
          </div>
          <Badge tone="accent">-{row.group.discountPercent}%</Badge>
          <PerPersonFee row={row} />
          <Can {...ACCESS_RULE.updateInvoice}>
            <GlassButton
              variant="ghost"
              size="sm"
              icon={<XCircle size={14} aria-hidden />}
              onClick={() => onCancel(row)}
            >
              Huỷ nhóm
            </GlassButton>
          </Can>
        </m.li>
      ))}
    </ul>
  );
}

/** Mọi thành viên cùng một khoá nên cùng một mức; lấy người đầu làm đại diện. */
function PerPersonFee({ row }: { readonly row: GroupRegistrationRow }) {
  const first = row.group.members[0];
  if (first === undefined) {
    return null;
  }
  return (
    <div className="text-right">
      <p className="text-xs text-mist-500 line-through">{formatter.count(first.originalTuitionFee)} đ</p>
      <p className="text-sm text-mist-100">{formatter.count(first.discountedTuitionFee)} đ / người</p>
    </div>
  );
}
