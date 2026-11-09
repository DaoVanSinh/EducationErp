import type { AllowanceFormRow } from "@/modules/teachers/model/teachers-forms";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassInput } from "@/shared/ui/glass-input";
import { Plus, Trash2 } from "lucide-react";

export interface TeacherAllowancesFieldProps {
  readonly rows: readonly AllowanceFormRow[];
  readonly error: string | undefined;
  readonly onChange: (rows: readonly AllowanceFormRow[]) => void;
}

const EMPTY_ROW: AllowanceFormRow = { name: "", amount: "" };

/** Thuần render + echo sự kiện ra ngoài qua onChange - không tự giữ state (Mandate #2). */
export function TeacherAllowancesField({ rows, error, onChange }: TeacherAllowancesFieldProps) {
  const updateRow = (index: number, patch: Partial<AllowanceFormRow>) =>
    onChange(rows.map((row, rowIndex) => (rowIndex === index ? { ...row, ...patch } : row)));

  return (
    <div className="flex flex-col gap-2">
      <span className="text-xs font-semibold tracking-wide text-slate-600 uppercase">Phụ cấp cố định hằng tháng</span>
      {rows.map((row, index) => (
        <div key={index} className="flex flex-col gap-2 sm:flex-row">
          <GlassInput
            aria-label={`Tên phụ cấp ${index + 1}`}
            placeholder="Tên phụ cấp"
            value={row.name}
            onChange={(event) => updateRow(index, { name: event.target.value })}
          />
          <GlassInput
            aria-label={`Số tiền phụ cấp ${index + 1}`}
            type="number"
            min={0}
            placeholder="Số tiền"
            value={row.amount}
            onChange={(event) => updateRow(index, { amount: event.target.value })}
          />
          <GlassButton
            type="button"
            variant="ghost"
            size="sm"
            onClick={() => onChange(rows.filter((_, rowIndex) => rowIndex !== index))}
            icon={<Trash2 size={14} aria-hidden />}
          >
            Xoá
          </GlassButton>
        </div>
      ))}
      {error ? <p className="text-xs text-rose-600">{error}</p> : null}
      <div>
        <GlassButton
          type="button"
          variant="secondary"
          size="sm"
          onClick={() => onChange([...rows, EMPTY_ROW])}
          icon={<Plus size={14} aria-hidden />}
        >
          Thêm phụ cấp
        </GlassButton>
      </div>
    </div>
  );
}
