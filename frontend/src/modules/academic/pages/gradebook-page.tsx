import { RequirePermission, usePermissions } from "@/entities/permission";
import { useGradebookController } from "@/modules/academic/hooks/use-gradebook-controller";
import { GradebookTable } from "@/modules/academic/ui/gradebook-table";
import { GradeScoreDialog } from "@/modules/academic/ui/grade-score-dialog";
import { ACCESS_RULE } from "@/shared/constants/permissions";
import { buildClassProgressPath } from "@/shared/constants/app-routes";
import { EmptyState } from "@/shared/ui/empty-state";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassPanel } from "@/shared/ui/glass-panel";
import { PageHeader } from "@/shared/ui/page-header";
import { Skeleton } from "@/shared/ui/skeleton";
import { Download, LineChart, Users } from "lucide-react";
import { Link, useParams } from "react-router-dom";

export function GradebookPage() {
  const { classId = "" } = useParams<{ classId: string }>();
  const controller = useGradebookController(classId);
  const permissions = usePermissions();
  const canEdit = permissions.allows(ACCESS_RULE.updateGrade);

  return (
    <RequirePermission {...ACCESS_RULE.readGrade}>
      <div className="flex flex-col gap-6">
        <PageHeader
          title="Sổ điểm"
          description="Điểm từng cột theo trọng số đã cấu hình ở khóa học. Điểm tổng kết chỉ hiện khi đủ điểm mọi cột."
          actions={
            <div className="flex gap-2">
              <Link to={buildClassProgressPath(classId)}>
                <GlassButton variant="secondary" size="sm" icon={<LineChart size={14} aria-hidden />}>
                  Tiến trình học tập
                </GlassButton>
              </Link>
              {/* Không bọc thêm <Can readGrade>: cả trang đã nằm trong RequirePermission cùng rule
                  đó, bọc lại là một lớp kiểm tra chết. */}
              <GlassButton
                size="sm"
                loading={controller.isDownloading}
                onClick={controller.onDownload}
                icon={<Download size={14} aria-hidden />}
              >
                Tải Excel
              </GlassButton>
            </div>
          }
        />

        {/* Nguồn sự thật về quyền là lỗi thật từ backend: 403 ACADEMIC_NOT_ASSIGNED_TO_CLASS hiện ở
            đây, giao diện không tự kết luận "tôi là giáo viên lớp này" (spec mục 10). */}
        {controller.gradebook.isError ? <ErrorNotice error={controller.gradebook.error} /> : null}
        {controller.downloadError ? <ErrorNotice error={controller.downloadError} /> : null}
        {controller.gradebook.isPending ? <Skeleton className="h-40" /> : null}

        {controller.gradebook.data ? (
          <GlassPanel className="flex flex-col gap-4">
            <GradebookBody controller={controller} canEdit={canEdit} />
          </GlassPanel>
        ) : null}

        {controller.editing === null ? null : (
          <GradeScoreDialog
            key={`${controller.editing.row.enrollmentId}-${controller.editing.column.id}`}
            classId={classId}
            row={controller.editing.row}
            column={controller.editing.column}
            open
            onClose={controller.stopEditing}
          />
        )}
      </div>
    </RequirePermission>
  );
}

/** Tách ra để trang không rẽ nhánh trong JSX (Mandate #3). */
function GradebookBody({
  controller,
  canEdit,
}: {
  readonly controller: ReturnType<typeof useGradebookController>;
  readonly canEdit: boolean;
}) {
  const gradebook = controller.gradebook.data;
  if (!gradebook) {
    return null;
  }
  if (gradebook.columns.length === 0) {
    return (
      <EmptyState
        icon={<Users size={28} aria-hidden />}
        title="Khóa học chưa cấu hình cột điểm"
        description="Mở màn hình Khóa học và thêm các cột điểm (tổng trọng số 100%) trước khi nhập điểm."
      />
    );
  }
  if (gradebook.rows.length === 0) {
    return (
      <EmptyState
        icon={<Users size={28} aria-hidden />}
        title="Lớp chưa có học viên nào"
        description="Ghi danh học viên vào lớp này rồi quay lại để nhập điểm."
      />
    );
  }
  return (
    <GradebookTable
      columns={gradebook.columns}
      rows={gradebook.rows}
      canEdit={canEdit}
      onEditScore={controller.startEditing}
    />
  );
}
