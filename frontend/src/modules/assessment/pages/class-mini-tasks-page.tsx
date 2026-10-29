import { Can, RequirePermission } from "@/entities/permission";
import { useClassMiniTasksController } from "@/modules/assessment/hooks/use-class-mini-tasks-controller";
import { MiniTaskFormDialog } from "@/modules/assessment/ui/mini-task-form-dialog";
import { MiniTaskList } from "@/modules/assessment/ui/mini-task-list";
import { MiniTaskPracticeTable } from "@/modules/assessment/ui/mini-task-practice-table";
import { buildClassAssignmentsPath } from "@/shared/constants/app-routes";
import { ACCESS_RULE } from "@/shared/constants/permissions";
import { EmptyState } from "@/shared/ui/empty-state";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassPanel } from "@/shared/ui/glass-panel";
import { PageHeader } from "@/shared/ui/page-header";
import { Skeleton } from "@/shared/ui/skeleton";
import { ListChecks, NotebookPen, Plus } from "lucide-react";
import { Link, useParams } from "react-router-dom";

export function ClassMiniTasksPage() {
  const { classId = "" } = useParams<{ classId: string }>();
  const controller = useClassMiniTasksController(classId);
  const data = controller.board.data;

  return (
    <RequirePermission {...ACCESS_RULE.readAssignment}>
      <div className="flex flex-col gap-6">
        <PageHeader
          title="Mini-task luyện tập"
          description="Bộ câu hỏi trắc nghiệm học viên tự làm trên hệ thống, chấm tự động, làm lại không giới hạn để ôn luyện."
          actions={
            <div className="flex gap-2">
              <Link to={buildClassAssignmentsPath(classId)}>
                <GlassButton variant="secondary" size="sm" icon={<NotebookPen size={14} aria-hidden />}>Bài tập & mini-test</GlassButton>
              </Link>
              <Can {...ACCESS_RULE.createAssignment}>
                <GlassButton size="sm" onClick={controller.openCreate} icon={<Plus size={14} aria-hidden />}>Tạo mini-task</GlassButton>
              </Can>
            </div>
          }
        />
        {controller.board.isError ? <ErrorNotice error={controller.board.error} /> : null}
        {controller.board.isPending ? <Skeleton className="h-40" /> : null}
        {data && data.miniTasks.length === 0 ? (
          <GlassPanel>
            <EmptyState icon={<ListChecks size={28} aria-hidden />} title="Lớp chưa có mini-task nào"
              description="Bấm Tạo mini-task để soạn bộ câu hỏi ôn luyện đầu tiên." />
          </GlassPanel>
        ) : null}
        {data && data.miniTasks.length > 0 ? (
          <>
            <MiniTaskList miniTasks={data.miniTasks} onEdit={controller.openEdit} />
            <GlassPanel className="flex flex-col gap-3">
              <h2 className="text-sm font-semibold text-mist-100">Kết quả luyện tập của học viên</h2>
              <MiniTaskPracticeTable miniTasks={data.miniTasks} rows={data.rows} />
            </GlassPanel>
          </>
        ) : null}
        {controller.formTarget === null ? null : (
          <MiniTaskFormDialog classId={classId} target={controller.formTarget} onClose={controller.closeForm} />
        )}
      </div>
    </RequirePermission>
  );
}
