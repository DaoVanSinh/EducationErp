import { RequirePermission } from "@/entities/permission";
import { useMyAssessmentsPageController } from "@/modules/learning/hooks/use-my-assessments-page-controller";
import { AssessmentClassCard } from "@/modules/learning/ui/assessment-class-card";
import { ACCESS_RULE } from "@/shared/constants/permissions";
import { EmptyState } from "@/shared/ui/empty-state";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { GlassPanel } from "@/shared/ui/glass-panel";
import { PageHeader } from "@/shared/ui/page-header";
import { Skeleton } from "@/shared/ui/skeleton";
import { NotebookPen } from "lucide-react";

export function MyAssessmentsPage() {
  return (
    <RequirePermission {...ACCESS_RULE.readLearning}>
      <div className="flex flex-col gap-6">
        <PageHeader
          title="Bài tập & luyện tập"
          description="Tình trạng nộp bài, điểm và nhận xét của giáo viên; làm mini-task để tự ôn luyện ngay trên hệ thống."
        />
        <MyAssessmentsContent />
      </div>
    </RequirePermission>
  );
}

function MyAssessmentsContent() {
  const controller = useMyAssessmentsPageController();

  if (controller.error) {
    return <ErrorNotice error={controller.error} />;
  }
  if (controller.isPending) {
    return <Skeleton className="h-48" />;
  }
  if (controller.isEmpty) {
    return (
      <GlassPanel>
        <EmptyState
          icon={<NotebookPen size={28} aria-hidden />}
          title="Bạn chưa ghi danh lớp nào"
          description="Bài tập và mini-task của các lớp bạn học sẽ hiện ở đây."
        />
      </GlassPanel>
    );
  }
  return (
    <div className="flex flex-col gap-4">
      {controller.classes.map((item) => (
        <AssessmentClassCard key={item.enrollmentId} item={item} />
      ))}
    </div>
  );
}
