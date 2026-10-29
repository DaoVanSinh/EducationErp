import { Can, RequirePermission } from "@/entities/permission";
import { useClassAssignmentsController } from "@/modules/assessment/hooks/use-class-assignments-controller";
import { AssignmentFormDialog } from "@/modules/assessment/ui/assignment-form-dialog";
import { AssignmentList } from "@/modules/assessment/ui/assignment-list";
import { AssignmentProgressTable } from "@/modules/assessment/ui/assignment-progress-table";
import { AssignmentResultsDialog } from "@/modules/assessment/ui/assignment-results-dialog";
import { buildClassMiniTasksPath } from "@/shared/constants/app-routes";
import { ACCESS_RULE } from "@/shared/constants/permissions";
import { EmptyState } from "@/shared/ui/empty-state";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassPanel } from "@/shared/ui/glass-panel";
import { PageHeader } from "@/shared/ui/page-header";
import { Skeleton } from "@/shared/ui/skeleton";
import { ListChecks, NotebookPen, Plus } from "lucide-react";
import { Link, useParams } from "react-router-dom";

export function ClassAssignmentsPage() {
  const { classId = "" } = useParams<{ classId: string }>();
  const controller = useClassAssignmentsController(classId);
  const data = controller.board.data;

  return (
    <RequirePermission {...ACCESS_RULE.readAssignment}>
      <div className="flex flex-col gap-6">
        <PageHeader
          title="Bài tập & mini-test"
          description="Giao bài định kỳ, ghi nhận tình trạng nộp và điểm, theo dõi tỷ lệ hoàn thành của từng học viên."
          actions={
            <div className="flex gap-2">
              <Link to={buildClassMiniTasksPath(classId)}>
                <GlassButton variant="secondary" size="sm" icon={<ListChecks size={14} aria-hidden />}>Mini-task</GlassButton>
              </Link>
              <Can {...ACCESS_RULE.createAssignment}>
                <GlassButton size="sm" onClick={controller.openCreate} icon={<Plus size={14} aria-hidden />}>Giao bài</GlassButton>
              </Can>
            </div>
          }
        />
        {controller.board.isError ? <ErrorNotice error={controller.board.error} /> : null}
        {controller.board.isPending ? <Skeleton className="h-40" /> : null}
        {data && data.assignments.length === 0 ? (
          <GlassPanel>
            <EmptyState icon={<NotebookPen size={28} aria-hidden />} title="Lớp chưa có bài nào"
              description="Bấm Giao bài để tạo bài tập hoặc mini-test đầu tiên." />
          </GlassPanel>
        ) : null}
        {data && data.assignments.length > 0 ? (
          <>
            <AssignmentList assignments={data.assignments} onRecordResults={controller.openResults} onEdit={controller.openEdit} />
            <GlassPanel className="flex flex-col gap-3">
              <h2 className="text-sm font-semibold text-mist-100">Tiến độ làm bài của học viên</h2>
              <AssignmentProgressTable assignments={data.assignments} rows={data.rows} />
            </GlassPanel>
          </>
        ) : null}
        {controller.formTarget === null ? null : (
          <AssignmentFormDialog classId={classId} target={controller.formTarget} onClose={controller.closeForm} />
        )}
        {data && controller.resultsTarget ? (
          <AssignmentResultsDialog classId={classId} assignment={controller.resultsTarget} rows={data.rows}
            onClose={controller.closeResults} />
        ) : null}
      </div>
    </RequirePermission>
  );
}
