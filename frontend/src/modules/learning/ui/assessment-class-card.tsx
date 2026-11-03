import {
  ASSIGNMENT_TYPE_LABEL,
  isMissingWork,
  SUBMISSION_STATUS,
  SUBMISSION_STATUS_LABEL,
  type MyAssessmentClass,
  type MyAssignmentItem,
  type MyMiniTaskItem,
  type SubmissionStatus,
} from "@/entities/assessment";
import {
  assignmentActionLabel,
  isAwaitingGrade,
  isReadOnlyEnrollment,
  showsClassStatus,
} from "@/modules/learning/model/learning-rules";
import { ENROLLMENT_STATUS_LABEL } from "@/shared/api/schemas";
import { buildPracticeMiniTaskPath } from "@/shared/constants/app-routes";
import { formatter } from "@/shared/lib/format";
import { Badge } from "@/shared/ui/badge";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassPanel } from "@/shared/ui/glass-panel";
import { PlayCircle, Send } from "lucide-react";
import { Link } from "react-router-dom";

export interface AssessmentClassCardProps {
  readonly item: MyAssessmentClass;
  readonly today: string;
  readonly onOpenAssignment: (assignment: MyAssignmentItem) => void;
}

const SUBMISSION_TONE: Record<SubmissionStatus, "positive" | "warning" | "neutral"> = {
  [SUBMISSION_STATUS.submitted]: "positive",
  [SUBMISSION_STATUS.late]: "warning",
  [SUBMISSION_STATUS.notSubmitted]: "neutral",
};

/** Bài tập/mini-test (nộp bài, tình trạng, điểm, nhận xét) và mini-task (điểm cao nhất, Đạt, nút làm bài) của một lớp. */
export function AssessmentClassCard({ item, today, onOpenAssignment }: AssessmentClassCardProps) {
  const readOnly = isReadOnlyEnrollment(item.enrollmentStatus);
  return (
    <GlassPanel className="flex flex-col gap-4">
      <header className="flex flex-wrap items-start justify-between gap-2">
        <div className="min-w-0">
          <h2 className="truncate text-base font-semibold text-mist-100">{item.courseName ?? "(đã xoá)"}</h2>
          <p className="text-sm font-medium text-mist-100">Lớp {item.classCode ?? "(đã xoá)"}</p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          {showsClassStatus(item.enrollmentStatus) ? null : (
            <Badge tone="neutral">{ENROLLMENT_STATUS_LABEL[item.enrollmentStatus]}</Badge>
          )}
          <Badge tone="accent">
            Hoàn thành: {item.completionPercent === null ? "chưa có bài" : `${item.completionPercent}%`}
          </Badge>
        </div>
      </header>

      <section className="flex flex-col gap-2">
        <h3 className="text-sm font-semibold text-mist-100">Bài tập & mini-test</h3>
        {item.assignments.length === 0 ? <p className="text-xs text-mist-400">Chưa có bài nào.</p> : null}
        {item.assignments.map((assignment) => (
          <AssignmentRow
            key={assignment.assignmentId}
            assignment={assignment}
            today={today}
            readOnly={readOnly}
            onOpen={onOpenAssignment}
          />
        ))}
      </section>

      <section className="flex flex-col gap-2">
        <h3 className="text-sm font-semibold text-mist-100">Mini-task tự ôn luyện</h3>
        {item.miniTasks.length === 0 ? <p className="text-xs text-mist-400">Chưa có mini-task nào.</p> : null}
        {item.miniTasks.map((task) => (
          <MiniTaskRow key={task.miniTaskId} task={task} readOnly={readOnly} />
        ))}
      </section>
    </GlassPanel>
  );
}

interface AssignmentRowProps {
  readonly assignment: MyAssignmentItem;
  readonly today: string;
  readonly readOnly: boolean;
  readonly onOpen: (assignment: MyAssignmentItem) => void;
}

function AssignmentRow({ assignment, today, readOnly, onOpen }: AssignmentRowProps) {
  const missing = !readOnly && isMissingWork(assignment.dueDate, assignment.status, today);
  const actionLabel = assignmentActionLabel(assignment);
  return (
    <div className="flex flex-wrap items-center justify-between gap-2 border-t border-white/40 pt-2">
      <div className="min-w-0">
        <p className="text-sm text-mist-100">{ASSIGNMENT_TYPE_LABEL[assignment.type]}: {assignment.title}</p>
        <p className="text-xs text-mist-400">
          Hạn nộp {assignment.dueDate ? formatter.date(assignment.dueDate) : "không đặt"}
          {assignment.feedback ? ` · Nhận xét: ${assignment.feedback}` : ""}
        </p>
      </div>
      <div className="flex flex-wrap items-center gap-2">
        {missing ? (
          <Badge tone="danger">Quá hạn</Badge>
        ) : (
          <Badge tone={SUBMISSION_TONE[assignment.status]}>{SUBMISSION_STATUS_LABEL[assignment.status]}</Badge>
        )}
        {isAwaitingGrade(assignment) ? <span className="text-xs text-mist-400">Chờ chấm</span> : null}
        {assignment.score === null ? null : (
          <span className="text-sm text-mist-100">{assignment.score}/{assignment.maxScore}</span>
        )}
        {actionLabel === null ? null : (
          <GlassButton
            size="sm"
            variant={assignment.canSubmit ? "primary" : "secondary"}
            onClick={() => onOpen(assignment)}
            icon={<Send size={14} aria-hidden />}
          >
            {actionLabel}
          </GlassButton>
        )}
      </div>
    </div>
  );
}

function MiniTaskRow({ task, readOnly }: { readonly task: MyMiniTaskItem; readonly readOnly: boolean }) {
  return (
    <div className="flex flex-wrap items-center justify-between gap-2 border-t border-white/40 pt-2">
      <div className="min-w-0">
        <p className="text-sm text-mist-100">{task.title}</p>
        <p className="text-xs text-mist-400">
          {task.questionCount} câu · {task.attemptCount} lượt đã làm · đạt từ {task.passPercent}%
          {task.bestScorePercent === null ? "" : ` · Cao nhất ${task.bestScorePercent}%`}
        </p>
      </div>
      <div className="flex items-center gap-2">
        {task.passed ? <Badge tone="positive">Đạt</Badge> : null}
        {readOnly ? null : (
          <Link to={buildPracticeMiniTaskPath(task.miniTaskId)}>
            <GlassButton size="sm" variant={task.passed ? "secondary" : "primary"} icon={<PlayCircle size={14} aria-hidden />}>
              {task.attemptCount === 0 ? "Làm bài" : "Làm lại"}
            </GlassButton>
          </Link>
        )}
      </div>
    </div>
  );
}
