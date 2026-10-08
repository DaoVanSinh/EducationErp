import type { AllowancePayload } from "@/entities/payroll";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassInput } from "@/shared/ui/glass-input";
import { Plus, Trash2 } from "lucide-react";

export interface AllowancesFieldListProps {
  readonly allowances: readonly AllowancePayload[];
  readonly onChange: (allowances: readonly AllowancePayload[]) => void;
}

/** Thuần render + echo sự kiện ra ngoài qua onChange - không tự giữ state (Mandate #2). */
export function AllowancesFieldList({ allowances, onChange }: AllowancesFieldListProps) {
  return (
    <div className="flex flex-col gap-2">
      <span className="text-xs font-semibold tracking-wide text-slate-600 uppercase">Phụ cấp</span>
      {allowances.map((allowance, index) => (
        <div key={index} className="flex gap-2">
          <GlassInput
            placeholder="Tên phụ cấp"
            value={allowance.name}
            onChange={(event) =>
              onChange(allowances.map((a, i) => (i === index ? { ...a, name: event.target.value } : a)))
            }
          />
          <GlassInput
            type="number"
            placeholder="Số tiền"
            value={allowance.amount}
            onChange={(event) =>
              onChange(allowances.map((a, i) => (i === index ? { ...a, amount: Number(event.target.value) } : a)))
            }
          />
          <GlassButton
            type="button"
            variant="ghost"
            size="sm"
            onClick={() => onChange(allowances.filter((_, i) => i !== index))}
            icon={<Trash2 size={14} aria-hidden />}
          >
            Xoá
          </GlassButton>
        </div>
      ))}
      <GlassButton
        type="button"
        variant="secondary"
        size="sm"
        onClick={() => onChange([...allowances, { name: "", amount: 0 }])}
        icon={<Plus size={14} aria-hidden />}
      >
        Thêm phụ cấp
      </GlassButton>
    </div>
  );
}
