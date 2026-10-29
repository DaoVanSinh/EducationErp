import { RequirePermission } from "@/entities/permission";
import { useMyAttendancePageController } from "@/modules/learning/hooks/use-my-attendance-page-controller";
import { AttendanceClassCard } from "@/modules/learning/ui/attendance-class-card";
import { ACCESS_RULE } from "@/shared/constants/permissions";
import { EmptyState } from "@/shared/ui/empty-state";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { GlassPanel } from "@/shared/ui/glass-panel";
import { PageHeader } from "@/shared/ui/page-header";
import { Skeleton } from "@/shared/ui/skeleton";
import { ClipboardCheck } from "lucide-react";

export function MyAttendancePage() {
  return (
    <RequirePermission {...ACCESS_RULE.readLearning}>
      <div className="flex flex-col gap-6">
        <PageHeader
          title="Điểm danh của tôi"
          description="Tỷ lệ chuyên cần và trạng thái từng buổi học ở mỗi lớp bạn đã ghi danh."
        />
        <MyAttendanceContent />
      </div>
    </RequirePermission>
  );
}

function MyAttendanceContent() {
  const controller = useMyAttendancePageController();

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
          icon={<ClipboardCheck size={28} aria-hidden />}
          title="Bạn chưa ghi danh lớp nào"
          description="Khi trung tâm ghi danh bạn vào một lớp, điểm danh của lớp đó sẽ hiện ở đây."
        />
      </GlassPanel>
    );
  }
  return (
    <div className="flex flex-col gap-4">
      {controller.classes.map((item) => (
        <AttendanceClassCard key={item.enrollmentId} item={item} />
      ))}
    </div>
  );
}
