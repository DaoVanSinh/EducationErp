import { RequirePermission } from "@/entities/permission";
import { useMyLearningPageController } from "@/modules/learning/hooks/use-my-learning-page-controller";
import { LearningClassCard } from "@/modules/learning/ui/learning-class-card";
import { ACCESS_RULE } from "@/shared/constants/permissions";
import { EmptyState } from "@/shared/ui/empty-state";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { GlassPanel } from "@/shared/ui/glass-panel";
import { PageHeader } from "@/shared/ui/page-header";
import { Skeleton } from "@/shared/ui/skeleton";
import { GraduationCap } from "lucide-react";

export function MyLearningPage() {
  return (
    <RequirePermission {...ACCESS_RULE.readLearning}>
      <div className="flex flex-col gap-6">
        <PageHeader
          title="Học tập của tôi"
          description="Lớp đã ghi danh, lịch học, giáo viên, điểm từng cột và tiến trình học tập."
        />
        <MyLearningContent />
      </div>
    </RequirePermission>
  );
}

function MyLearningContent() {
  const controller = useMyLearningPageController();

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
          icon={<GraduationCap size={28} aria-hidden />}
          title="Bạn chưa ghi danh lớp nào"
          description="Khi trung tâm ghi danh bạn vào một lớp, lớp đó sẽ hiện ở đây cùng lịch học và điểm."
        />
      </GlassPanel>
    );
  }
  return (
    <div className="flex flex-col gap-4">
      {controller.classes.map((item) => (
        <LearningClassCard key={item.enrollmentId} item={item} today={controller.today} />
      ))}
    </div>
  );
}
