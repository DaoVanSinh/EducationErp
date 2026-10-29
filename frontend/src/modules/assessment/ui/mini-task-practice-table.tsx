import type { MiniTask, StudentMiniTaskRow } from "@/entities/assessment";
import { percentTone } from "@/modules/assessment/model/assessment-rules";
import { ENROLLMENT_STATUS_LABEL } from "@/shared/api/schemas";
import { Badge } from "@/shared/ui/badge";

export interface MiniTaskPracticeTableProps {
  readonly miniTasks: readonly MiniTask[];
  readonly rows: readonly StudentMiniTaskRow[];
}

/** Tiến độ tự ôn luyện: học viên × mini-task (điểm cao nhất, số lượt làm). */
export function MiniTaskPracticeTable({ miniTasks, rows }: MiniTaskPracticeTableProps) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[560px] text-left">
        <thead>
          <tr className="text-xs text-mist-500">
            <th scope="col" className="p-2 font-normal">Học viên</th>
            {miniTasks.map((task) => (
              <th key={task.id} scope="col" className="p-2 font-normal">{task.title}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={row.enrollmentId} className="border-t border-white/40">
              <td className="p-2">
                <p className="text-sm text-mist-100">{row.studentName ?? "Học viên"}</p>
                <p className="text-xs text-mist-400">{ENROLLMENT_STATUS_LABEL[row.enrollmentStatus]}</p>
              </td>
              {miniTasks.map((task) => (
                <td key={task.id} className="p-2">
                  <BestScoreCell best={row.bestScoreByMiniTaskId[task.id]} attempts={row.attemptsByMiniTaskId[task.id]} />
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function BestScoreCell({ best, attempts }: { readonly best: number | undefined; readonly attempts: number | undefined }) {
  if (best === undefined) {
    return <span className="text-xs text-mist-400">Chưa làm</span>;
  }
  return (
    <div className="flex flex-col gap-1">
      <Badge tone={percentTone(best)}>{best}%</Badge>
      <span className="text-xs text-mist-400">{attempts ?? 0} lượt</span>
    </div>
  );
}
