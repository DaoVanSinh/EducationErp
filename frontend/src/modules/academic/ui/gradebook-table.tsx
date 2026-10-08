import type { StudentGradesRow } from "@/entities/academic";
import { ENROLLMENT_STATUS_LABEL, type GradeColumn } from "@/shared/api/schemas";
import { Badge } from "@/shared/ui/badge";
import { GlassButton } from "@/shared/ui/glass-button";

export interface GradebookTableProps {
  readonly columns: readonly GradeColumn[];
  readonly rows: readonly StudentGradesRow[];
  readonly canEdit: boolean;
  readonly onEditScore: (row: StudentGradesRow, column: GradeColumn) => void;
}

/** Điểm tổng kết null = "chưa đủ điểm" (backend GradeRules), KHÔNG được hiển thị 0. */
function FinalScoreCell({ finalScore }: { readonly finalScore: number | null }) {
  if (finalScore === null) {
    return <Badge tone="neutral">Chưa đủ điểm</Badge>;
  }
  return <span className="text-sm text-mist-100">{finalScore}</span>;
}

function ScoreCell({
  row,
  column,
  canEdit,
  onEditScore,
}: {
  readonly row: StudentGradesRow;
  readonly column: GradeColumn;
  readonly canEdit: boolean;
  readonly onEditScore: (row: StudentGradesRow, column: GradeColumn) => void;
}) {
  const score = row.scoresByColumnId[column.id];
  const label = score === undefined ? "—" : String(score);

  if (!canEdit) {
    return <span className="text-sm text-mist-100">{label}</span>;
  }
  return (
    <GlassButton
      variant="ghost"
      size="sm"
      onClick={() => onEditScore(row, column)}
      aria-label={`Sửa điểm ${column.name} của ${row.studentName ?? "học viên"}`}
    >
      {label}
    </GlassButton>
  );
}

export function GradebookTable({ columns, rows, canEdit, onEditScore }: GradebookTableProps) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[640px] text-left">
        <thead>
          <tr className="text-xs text-mist-500">
            <th scope="col" className="p-2 font-normal">Học viên</th>
            <th scope="col" className="p-2 font-normal">Trạng thái</th>
            {columns.map((column) => (
              <th key={column.id} scope="col" className="p-2 font-normal">
                {column.name} ({column.weightPercent}% · thang {column.maxScore})
              </th>
            ))}
            <th scope="col" className="p-2 font-normal">Điểm tổng kết</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={row.enrollmentId} className="glass rounded-2xl">
              <td className="p-2 text-sm text-mist-100">{row.studentName ?? "—"}</td>
              <td className="p-2 text-xs text-mist-400">{ENROLLMENT_STATUS_LABEL[row.status]}</td>
              {columns.map((column) => (
                <td key={column.id} className="p-2">
                  <ScoreCell row={row} column={column} canEdit={canEdit} onEditScore={onEditScore} />
                </td>
              ))}
              <td className="p-2">
                <FinalScoreCell finalScore={row.finalScore} />
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
