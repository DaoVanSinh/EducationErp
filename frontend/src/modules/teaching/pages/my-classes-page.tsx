import { RequirePermission } from "@/entities/permission";
import { useMyClassesPageController } from "@/modules/teaching/hooks/use-my-classes-page-controller";
import { TeachingClassCard } from "@/modules/teaching/ui/teaching-class-card";
import { ACCESS_RULE } from "@/shared/constants/permissions";
import { EmptyState } from "@/shared/ui/empty-state";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { GlassPanel } from "@/shared/ui/glass-panel";
import { PageHeader } from "@/shared/ui/page-header";
import { Pagination } from "@/shared/ui/pagination";
import { Skeleton } from "@/shared/ui/skeleton";
import { CalendarDays } from "lucide-react";

export function MyClassesPage() {
  return (
    <RequirePermission {...ACCESS_RULE.readTeaching}>
      <div className="flex flex-col gap-6">
        <PageHeader
          title="Lớp của tôi"
          description="Các lớp bạn được phân công dạy. Mở sổ điểm để xem danh sách học viên và nhập điểm."
        />
        <MyClassesContent />
      </div>
    </RequirePermission>
  );
}

function MyClassesContent() {
  const controller = useMyClassesPageController();

  if (controller.error) {
    return <ErrorNotice error={controller.error} />;
  }
  if (controller.isPending || !controller.data) {
    return <Skeleton className="h-48" />;
  }
  if (controller.isEmpty) {
    return (
      <GlassPanel>
        <EmptyState
          icon={<CalendarDays size={28} aria-hidden />}
          title="Bạn chưa được phân công lớp nào"
          description="Khi trung tâm phân công bạn dạy một lớp, lớp đó sẽ hiện ở đây."
        />
      </GlassPanel>
    );
  }
  return (
    <div className="flex flex-col gap-4">
      <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
        {controller.data.items.map((cls) => (
          <TeachingClassCard key={cls.id} cls={cls} today={controller.today} />
        ))}
      </div>
      <Pagination
        page={controller.data.page}
        totalPages={controller.data.totalPages}
        totalItems={controller.data.totalItems}
        onPageChange={controller.onPageChange}
        itemLabel="lớp"
      />
    </div>
  );
}
