import { CONTRACT_STATUS, CONTRACT_STATUS_LABEL, CONTRACT_TYPE_LABEL, type ContractSummary } from "@/entities/payroll";
import { Can } from "@/entities/permission";
import { ACCESS_RULE } from "@/shared/constants/permissions";
import { Badge } from "@/shared/ui/badge";
import { GlassButton } from "@/shared/ui/glass-button";
import { Download, Pencil, UserX } from "lucide-react";

export interface ContractsTableProps {
  readonly rows: readonly ContractSummary[];
  readonly onEdit: (contract: ContractSummary) => void;
  readonly onTerminate: (contractId: string) => void;
  readonly onDownload: (contractId: string) => void;
  readonly isTerminating: boolean;
}

export function ContractsTable({ rows, onEdit, onTerminate, onDownload, isTerminating }: ContractsTableProps) {
  return (
    <ul className="flex flex-col gap-2">
      {rows.map((contract) => (
        <li
          key={contract.id}
          className="glass grid grid-cols-1 items-center gap-3 rounded-2xl p-3 lg:grid-cols-[minmax(0,2fr)_minmax(0,1fr)_auto_auto_auto_auto]"
        >
          <div className="min-w-0">
            <p className="truncate text-sm text-mist-100">{contract.accountFullName ?? contract.accountEmail}</p>
            <p className="truncate text-xs text-mist-500">{CONTRACT_TYPE_LABEL[contract.contractType]}</p>
          </div>
          <Badge tone={contract.status === CONTRACT_STATUS.active ? "positive" : "neutral"}>
            {CONTRACT_STATUS_LABEL[contract.status]}
          </Badge>
          <Can {...ACCESS_RULE.updatePayroll}>
            <GlassButton variant="secondary" size="sm" onClick={() => onEdit(contract)} icon={<Pencil size={14} aria-hidden />}>
              Sửa
            </GlassButton>
          </Can>
          {contract.contractFileKey === null ? null : (
            <GlassButton
              variant="ghost"
              size="sm"
              onClick={() => onDownload(contract.id)}
              icon={<Download size={14} aria-hidden />}
            >
              Tải file
            </GlassButton>
          )}
          <Can {...ACCESS_RULE.updatePayroll}>
            {contract.status === CONTRACT_STATUS.active ? (
              <GlassButton
                variant="ghost"
                size="sm"
                loading={isTerminating}
                onClick={() => onTerminate(contract.id)}
                icon={<UserX size={14} aria-hidden />}
              >
                Kết thúc hợp đồng
              </GlassButton>
            ) : null}
          </Can>
        </li>
      ))}
    </ul>
  );
}
