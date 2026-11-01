import { REQUIRED_TOTAL_WEIGHT_PERCENT, WEIGHT_TOLERANCE } from "@/shared/api/schemas";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassInput } from "@/shared/ui/glass-input";
import { Plus, X } from "lucide-react";

/** Giá trị ô vẫn là chuỗi: GlassInput là input thường, Zod mới đổi sang số khi submit. `id` null =
 * cột MỚI; giữ nguyên `id` của cột đang sửa để backend không mồ côi điểm đã nhập cho nó (Final
 * review Critical #1). */
export interface GradeColumnRow {
  readonly id: string | null;
  readonly name: string;
  readonly weightPercent: string;
  readonly maxScore: string;
}

export interface GradeColumnEditorProps {
  readonly columns: readonly GradeColumnRow[];
  // Mutable, không readonly: nơi gọi truyền thẳng vào form.setValue("gradeColumns", ...), mà kiểu
  // trường đó do Zod suy ra từ schema (z.array(...) luôn là mảng mutable).
  readonly onChange: (columns: GradeColumnRow[]) => void;
}

const DEFAULT_MAX_SCORE = "10";

function totalWeightOf(columns: readonly GradeColumnRow[]): number {
  return columns.reduce((sum, column) => sum + Number(column.weightPercent || "0"), 0);
}

export function GradeColumnEditor({ columns, onChange }: GradeColumnEditorProps) {
  const total = totalWeightOf(columns);
  const isBalanced =
    columns.length === 0 || Math.abs(total - REQUIRED_TOTAL_WEIGHT_PERCENT) <= WEIGHT_TOLERANCE;

  const addColumn = () => {
    onChange([...columns, { id: null, name: "", weightPercent: "", maxScore: DEFAULT_MAX_SCORE }]);
  };

  const updateColumn = (index: number, patch: Partial<GradeColumnRow>) => {
    onChange(columns.map((column, i) => (i === index ? { ...column, ...patch } : column)));
  };

  const removeColumn = (index: number) => {
    onChange(columns.filter((_, i) => i !== index));
  };

  // GlassInput luôn w-full và cx không gộp class trùng, nên độ rộng từng ô đặt ở khung bọc ngoài.
  return (
    <div className="flex flex-col gap-2">
      {columns.length > 0 ? (
        <div className="flex items-center gap-2 text-xs font-medium text-slate-500">
          <span className="min-w-0 flex-1">Tên cột điểm</span>
          <span className="w-28 shrink-0">Trọng số (%)</span>
          <span className="w-24 shrink-0">Thang điểm</span>
          <span className="w-9 shrink-0" aria-hidden />
        </div>
      ) : null}
      {columns.map((column, index) => (
        <div key={index} className="flex items-center gap-2">
          <div className="min-w-0 flex-1">
            <GlassInput
              aria-label="Tên cột điểm"
              placeholder="Ví dụ: Giữa kỳ"
              value={column.name}
              onChange={(event) => updateColumn(index, { name: event.target.value })}
            />
          </div>
          <div className="w-28 shrink-0">
            <GlassInput
              aria-label="Trọng số (%)"
              type="number"
              min={0}
              step={0.01}
              placeholder="40"
              value={column.weightPercent}
              onChange={(event) => updateColumn(index, { weightPercent: event.target.value })}
            />
          </div>
          <div className="w-24 shrink-0">
            <GlassInput
              aria-label="Thang điểm"
              type="number"
              min={0}
              step={0.01}
              value={column.maxScore}
              onChange={(event) => updateColumn(index, { maxScore: event.target.value })}
            />
          </div>
          <GlassButton
            type="button"
            variant="ghost"
            size="sm"
            onClick={() => removeColumn(index)}
            aria-label="Xoá cột điểm"
          >
            <X size={14} aria-hidden />
          </GlassButton>
        </div>
      ))}

      <div className="flex items-center justify-between gap-2">
        <GlassButton
          type="button"
          variant="secondary"
          size="sm"
          onClick={addColumn}
          icon={<Plus size={14} aria-hidden />}
        >
          Thêm cột điểm
        </GlassButton>
        <GradeColumnTotal count={columns.length} total={total} isBalanced={isBalanced} />
      </div>
    </div>
  );
}

/** Tách ra để component chính không rẽ nhánh trong JSX (Mandate #3). */
function GradeColumnTotal({
  count,
  total,
  isBalanced,
}: {
  readonly count: number;
  readonly total: number;
  readonly isBalanced: boolean;
}) {
  if (count === 0) {
    return <p className="text-xs text-mist-500">Chưa cấu hình cột điểm nào.</p>;
  }
  return (
    <p className={isBalanced ? "text-xs text-mist-400" : "text-xs text-rose-300"}>
      Tổng trọng số: {total}% / {REQUIRED_TOTAL_WEIGHT_PERCENT}%
    </p>
  );
}
