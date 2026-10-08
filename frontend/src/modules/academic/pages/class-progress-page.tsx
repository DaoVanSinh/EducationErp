import { RequirePermission } from "@/entities/permission";
import { useClassProgressController } from "@/modules/academic/hooks/use-class-progress-controller";
import { ClassProgressTable } from "@/modules/academic/ui/class-progress-table";
import { buildClassGradebookPath } from "@/shared/constants/app-routes";
import { ACCESS_RULE } from "@/shared/constants/permissions";
import { EmptyState } from "@/shared/ui/empty-state";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassPanel } from "@/shared/ui/glass-panel";
import { PageHeader } from "@/shared/ui/page-header";
import { Skeleton } from "@/shared/ui/skeleton";
import { BookOpen, Download, Users } from "lucide-react";
import { Link, useParams } from "react-router-dom";

export function ClassProgressPage() {
  const { classId = "" } = useParams<{ classId: string }>();
  const controller = useClassProgressController(classId);

  return (
    <RequirePermission {...ACCESS_RULE.readGrade}>
      <div className="flex flex-col gap-6">
        <PageHeader
          title="Tiến trình học tập"
          description="Tự tính từ lịch học của lớp và ngày ghi danh của từng học viên — không ai nhập. Học viên đã rút được đóng băng tại thời điểm rút."
          actions={
            <div className="flex gap-2">
              <Link to={buildClassGradebookPath(classId)}>
                <GlassButton variant="secondary" size="sm" icon={<BookOpen size={14} aria-hidden />}>
                  Sổ điểm
                </GlassButton>
              </Link>
              {/* Cùng lý do với trang Sổ điểm: RequirePermission ở ngoài đã gác đúng rule này. */}
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

        {controller.progress.isError ? <ErrorNotice error={controller.progress.error} /> : null}
        {controller.downloadError ? <ErrorNotice error={controller.downloadError} /> : null}
        {controller.progress.isPending ? <Skeleton className="h-40" /> : null}

        {controller.progress.data ? (
          <GlassPanel className="flex flex-col gap-4">
            <ProgressBody controller={controller} />
          </GlassPanel>
        ) : null}
      </div>
    </RequirePermission>
  );
}

/** Tách ra để trang không rẽ nhánh trong JSX (Mandate #3). */
function ProgressBody({
  controller,
}: {
  readonly controller: ReturnType<typeof useClassProgressController>;
}) {
  const progress = controller.progress.data;
  if (!progress) {
    return null;
  }
  if (progress.rows.length === 0) {
    return (
      <EmptyState
        icon={<Users size={28} aria-hidden />}
        title="Lớp chưa có học viên nào"
        description="Ghi danh học viên vào lớp này rồi quay lại để xem tiến trình."
      />
    );
  }
  return (
    <>
      <p className="text-xs text-mist-500">
        {progress.standardSessionCount === null
          ? "Khóa học chưa cấu hình tổng số buổi chuẩn nên không tính được phần trăm tiến trình."
          : `Tổng số buổi chuẩn của khóa: ${progress.standardSessionCount}.`}
      </p>
      <ClassProgressTable rows={progress.rows} />
    </>
  );
}
