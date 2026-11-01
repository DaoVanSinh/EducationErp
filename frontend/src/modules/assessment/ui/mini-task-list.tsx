import { Can } from "@/entities/permission";
import type { MiniTask } from "@/entities/assessment";
import { percentLabel, percentTone } from "@/modules/assessment/model/assessment-rules";
import { ACCESS_RULE } from "@/shared/constants/permissions";
import { Badge } from "@/shared/ui/badge";
import { GlassButton } from "@/shared/ui/glass-button";
import { Pencil } from "lucide-react";

export interface MiniTaskListProps {
  readonly miniTasks: readonly MiniTask[];
  readonly onEdit: (task: MiniTask) => void;
}

/** Mini-task của lớp kèm số câu, số lượt làm, số học viên đã làm, trung bình điểm cao nhất. */
export function MiniTaskList({ miniTasks, onEdit }: MiniTaskListProps) {
  return (
    <ul className="grid grid-cols-1 gap-3 lg:grid-cols-2">
      {miniTasks.map((task) => (
        <li key={task.id} className="glass flex flex-col gap-3 rounded-2xl p-4">
          <header className="flex flex-wrap items-start justify-between gap-2">
            <div className="min-w-0">
              <h3 className="truncate text-sm font-semibold text-mist-100">{task.title}</h3>
              <p className="text-xs text-mist-400">{task.questions.length} câu hỏi trắc nghiệm · ngưỡng đạt {task.passPercent}%</p>
            </div>
            <Badge tone={task.active ? "positive" : "neutral"}>{task.active ? "Đang mở" : "Đã tắt"}</Badge>
          </header>
          {task.description ? <p className="text-xs text-mist-400">{task.description}</p> : null}
          <div className="flex flex-wrap items-center gap-2 text-sm text-mist-100">
            <span>{task.attemptCount} lượt làm · {task.studentsAttempted} học viên đã làm · {task.studentsPassed} đã đạt</span>
            <Badge tone={percentTone(task.averageBestScorePercent)}>
              TB điểm cao nhất: {percentLabel(task.averageBestScorePercent, "chưa có")}
            </Badge>
          </div>
          <Can {...ACCESS_RULE.updateAssignment}>
            <div>
              <GlassButton variant="secondary" size="sm" onClick={() => onEdit(task)} icon={<Pencil size={14} aria-hidden />}>
                Sửa câu hỏi
              </GlassButton>
            </div>
          </Can>
        </li>
      ))}
    </ul>
  );
}
