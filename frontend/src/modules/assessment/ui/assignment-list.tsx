import { Can } from "@/entities/permission";
import { ASSIGNMENT_TYPE_LABEL, type Assignment } from "@/entities/assessment";
import { ACCESS_RULE } from "@/shared/constants/permissions";
import { formatter } from "@/shared/lib/format";
import { Badge } from "@/shared/ui/badge";
import { GlassButton } from "@/shared/ui/glass-button";
import { ClipboardList, Pencil } from "lucide-react";

export interface AssignmentListProps {
  readonly assignments: readonly Assignment[];
  readonly onRecordResults: (assignment: Assignment) => void;
  readonly onEdit: (assignment: Assignment) => void;
}

/** Danh sách bài của lớp kèm số đã nộp / sĩ số và điểm trung bình. */
export function AssignmentList({ assignments, onRecordResults, onEdit }: AssignmentListProps) {
  return (
    <ul className="grid grid-cols-1 gap-3 lg:grid-cols-2">
      {assignments.map((assignment) => (
        <li key={assignment.id} className="glass flex flex-col gap-3 rounded-2xl p-4">
          <header className="flex flex-wrap items-start justify-between gap-2">
            <div className="min-w-0">
              <h3 className="truncate text-sm font-semibold text-mist-100">{assignment.title}</h3>
              <p className="text-xs text-mist-400">
                Hạn nộp: {assignment.dueDate ? formatter.date(assignment.dueDate) : "Không đặt"} · thang {assignment.maxScore}
              </p>
            </div>
            <div className="flex gap-1">
              <Badge tone="accent">{ASSIGNMENT_TYPE_LABEL[assignment.type]}</Badge>
              {assignment.active ? null : <Badge tone="neutral">Đã tắt</Badge>}
            </div>
          </header>
          {assignment.description ? <p className="text-xs text-mist-400">{assignment.description}</p> : null}
          <p className="text-sm text-mist-100">
            Đã nộp {assignment.submittedCount}/{assignment.rosterCount} · Điểm trung bình{" "}
            {assignment.averageScore === null ? "chưa có" : assignment.averageScore}
          </p>
          <Can {...ACCESS_RULE.updateAssignment}>
            <div className="flex flex-wrap gap-2">
              <GlassButton size="sm" onClick={() => onRecordResults(assignment)} icon={<ClipboardList size={14} aria-hidden />}>
                Nhập kết quả
              </GlassButton>
              <GlassButton variant="secondary" size="sm" onClick={() => onEdit(assignment)} icon={<Pencil size={14} aria-hidden />}>
                Sửa
              </GlassButton>
            </div>
          </Can>
        </li>
      ))}
    </ul>
  );
}
