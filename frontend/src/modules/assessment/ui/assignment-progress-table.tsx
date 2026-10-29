import { SUBMISSION_STATUS_LABEL, type Assignment, type StudentAssignmentRow } from "@/entities/assessment";
import { percentLabel, percentTone, submissionTone } from "@/modules/assessment/model/assessment-rules";
import { ENROLLMENT_STATUS_LABEL } from "@/shared/api/schemas";
import { Badge } from "@/shared/ui/badge";

export interface AssignmentProgressTableProps {
  readonly assignments: readonly Assignment[];
  readonly rows: readonly StudentAssignmentRow[];
}

/** Tiến độ làm bài: học viên × bài (tình trạng nộp, điểm) và tỷ lệ hoàn thành các bài đang bật. */
export function AssignmentProgressTable({ assignments, rows }: AssignmentProgressTableProps) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[640px] text-left">
        <thead>
          <tr className="text-xs text-mist-500">
            <th scope="col" className="p-2 font-normal">Học viên</th>
            {assignments.map((assignment) => (
              <th key={assignment.id} scope="col" className="p-2 font-normal">
                {assignment.title}
              </th>
            ))}
            <th scope="col" className="p-2 font-normal">Hoàn thành</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={row.enrollmentId} className="border-t border-white/40">
              <td className="p-2">
                <p className="text-sm text-mist-100">{row.studentName ?? "Học viên"}</p>
                <p className="text-xs text-mist-400">{ENROLLMENT_STATUS_LABEL[row.enrollmentStatus]}</p>
              </td>
              {assignments.map((assignment) => (
                <td key={assignment.id} className="p-2">
                  <ResultCellView row={row} assignmentId={assignment.id} />
                </td>
              ))}
              <td className="p-2">
                <Badge tone={percentTone(row.completionPercent)}>{percentLabel(row.completionPercent, "Chưa có bài")}</Badge>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function ResultCellView({ row, assignmentId }: { readonly row: StudentAssignmentRow; readonly assignmentId: string }) {
  const cell = row.resultsByAssignmentId[assignmentId];
  if (!cell) {
    return <span className="text-xs text-mist-400">Chưa nộp</span>;
  }
  return (
    <div className="flex flex-col gap-1" title={cell.feedback ?? undefined}>
      <Badge tone={submissionTone(cell.status)}>{SUBMISSION_STATUS_LABEL[cell.status]}</Badge>
      {cell.score === null ? null : <span className="text-xs text-mist-100">{cell.score} điểm</span>}
    </div>
  );
}
