import {
  ASSIGNMENT_TYPE_LABEL,
  SUBMISSION_STATUS,
  SUBMISSION_STATUS_LABEL,
  type MyAssessmentClass,
  type SubmissionStatus,
} from "@/entities/assessment";
import { buildPracticeMiniTaskPath } from "@/shared/constants/app-routes";
import { formatter } from "@/shared/lib/format";
import { Badge } from "@/shared/ui/badge";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassPanel } from "@/shared/ui/glass-panel";
import { PlayCircle } from "lucide-react";
import { Link } from "react-router-dom";

export interface AssessmentClassCardProps {
  readonly item: MyAssessmentClass;
}

const SUBMISSION_TONE: Record<SubmissionStatus, "positive" | "warning" | "neutral"> = {
  [SUBMISSION_STATUS.submitted]: "positive",
  [SUBMISSION_STATUS.late]: "warning",
  [SUBMISSION_STATUS.notSubmitted]: "neutral",
};

/** Bài tập/mini-test (tình trạng, điểm, nhận xét) và mini-task (điểm cao nhất, nút làm bài) của một lớp. */
export function AssessmentClassCard({ item }: AssessmentClassCardProps) {
  return (
    <GlassPanel className="flex flex-col gap-4">
      <header className="flex flex-wrap items-start justify-between gap-2">
        <div className="min-w-0">
          <h2 className="truncate text-base font-semibold text-mist-100">{item.courseName ?? "(đã xoá)"}</h2>
          <p className="text-xs text-mist-400">Lớp {item.classCode ?? "(đã xoá)"}</p>
        </div>
        <Badge tone="accent">
          Hoàn thành: {item.completionPercent === null ? "chưa có bài" : `${item.completionPercent}%`}
        </Badge>
      </header>

      <section className="flex flex-col gap-2">
        <h3 className="text-sm font-semibold text-mist-100">Bài tập & mini-test</h3>
        {item.assignments.length === 0 ? <p className="text-xs text-mist-400">Chưa có bài nào.</p> : null}
        {item.assignments.map((assignment) => (
          <div key={assignment.assignmentId} className="flex flex-wrap items-center justify-between gap-2 border-t border-white/40 pt-2">
            <div className="min-w-0">
              <p className="text-sm text-mist-100">{ASSIGNMENT_TYPE_LABEL[assignment.type]}: {assignment.title}</p>
              <p className="text-xs text-mist-400">
                Hạn nộp {assignment.dueDate ? formatter.date(assignment.dueDate) : "không đặt"}
                {assignment.feedback ? ` · Nhận xét: ${assignment.feedback}` : ""}
              </p>
            </div>
            <div className="flex items-center gap-2">
              <Badge tone={SUBMISSION_TONE[assignment.status]}>{SUBMISSION_STATUS_LABEL[assignment.status]}</Badge>
              {assignment.score === null ? null : (
                <span className="text-sm text-mist-100">{assignment.score}/{assignment.maxScore}</span>
              )}
            </div>
          </div>
        ))}
      </section>

      <section className="flex flex-col gap-2">
        <h3 className="text-sm font-semibold text-mist-100">Mini-task tự ôn luyện</h3>
        {item.miniTasks.length === 0 ? <p className="text-xs text-mist-400">Chưa có mini-task nào.</p> : null}
        {item.miniTasks.map((task) => (
          <div key={task.miniTaskId} className="flex flex-wrap items-center justify-between gap-2 border-t border-white/40 pt-2">
            <div className="min-w-0">
              <p className="text-sm text-mist-100">{task.title}</p>
              <p className="text-xs text-mist-400">
                {task.questionCount} câu · {task.attemptCount} lượt đã làm
                {task.bestScorePercent === null ? "" : ` · Cao nhất ${task.bestScorePercent}%`}
              </p>
            </div>
            <Link to={buildPracticeMiniTaskPath(task.miniTaskId)}>
              <GlassButton size="sm" icon={<PlayCircle size={14} aria-hidden />}>
                {task.attemptCount === 0 ? "Làm bài" : "Làm lại"}
              </GlassButton>
            </Link>
          </div>
        ))}
      </section>
    </GlassPanel>
  );
}
