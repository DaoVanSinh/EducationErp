import { Can, RequirePermission } from "@/entities/permission";
import { useEnrollmentsPageController } from "@/modules/enrollment/hooks/use-enrollments-page-controller";
import { CreateEnrollmentDialog } from "@/modules/enrollment/ui/create-enrollment-dialog";
import { EnrollmentsTable } from "@/modules/enrollment/ui/enrollments-table";
import { ACCESS_RULE } from "@/shared/constants/permissions";
import { EmptyState } from "@/shared/ui/empty-state";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassPanel } from "@/shared/ui/glass-panel";
import { GlassSelect } from "@/shared/ui/glass-select";
import { PageHeader } from "@/shared/ui/page-header";
import { Pagination } from "@/shared/ui/pagination";
import { Skeleton } from "@/shared/ui/skeleton";
import { ClipboardList, Plus } from "lucide-react";

export function EnrollmentsPage() {
  const controller = useEnrollmentsPageController();

  return (
    <RequirePermission {...ACCESS_RULE.readEnrollment}>
      <div className="flex flex-col gap-6">
        <PageHeader
          title="Ghi danh"
          description="Ghi danh học viên vào lớp. Rút khỏi lớp không xoá công nợ đã phát hành."
          actions={
            <Can {...ACCESS_RULE.createEnrollment}>
              <GlassButton onClick={controller.openCreateDialog} icon={<Plus size={16} aria-hidden />}>
                Ghi danh mới
              </GlassButton>
            </Can>
          }
        />

        {controller.enrollments.isError ? <ErrorNotice error={controller.enrollments.error} /> : null}

        <GlassPanel className="flex flex-col gap-4">
          <div className="flex flex-wrap gap-2">
            <GlassSelect
              aria-label="Lọc theo học viên"
              value={controller.studentFilter}
              onChange={(event) => controller.setStudentFilter(event.target.value)}
            >
              <option value="">Mọi học viên</option>
              {(controller.students.data?.items ?? []).map((student) => (
                <option key={student.id} value={student.id}>
                  {student.fullName ?? student.email ?? student.id}
                </option>
              ))}
            </GlassSelect>
            <GlassSelect
              aria-label="Lọc theo lớp"
              value={controller.classFilter}
              onChange={(event) => controller.setClassFilter(event.target.value)}
            >
              <option value="">Mọi lớp</option>
              {(controller.classes.data?.items ?? []).map((item) => (
                <option key={item.id} value={item.id}>
                  {item.code} — {item.courseName}
                </option>
              ))}
            </GlassSelect>
          </div>

          {controller.enrollments.isPending ? (
            <div className="flex flex-col gap-2">
              <Skeleton className="h-16" />
              <Skeleton className="h-16" />
            </div>
          ) : null}

          {controller.enrollments.data ? <EnrollmentsListSection controller={controller} /> : null}
        </GlassPanel>

        <CreateEnrollmentDialog
          open={controller.createDialogOpen}
          onClose={controller.closeCreateDialog}
          students={controller.students.data?.items ?? []}
          classes={controller.classes.data?.items ?? []}
        />
      </div>
    </RequirePermission>
  );
}

/** Tách nhánh rỗng/có dữ liệu ra component riêng - tránh nested ternary trong JSX (Mandate #3). */
function EnrollmentsListSection({
  controller,
}: {
  readonly controller: ReturnType<typeof useEnrollmentsPageController>;
}) {
  const page = controller.enrollments.data;
  if (!page || page.items.length === 0) {
    return (
      <EmptyState
        icon={<ClipboardList size={28} aria-hidden />}
        title="Chưa có ghi danh nào"
        description="Ghi danh học viên đầu tiên để bắt đầu phát hành học phí."
      />
    );
  }
  return (
    <>
      <EnrollmentsTable
        rows={page.items}
        onWithdraw={controller.onWithdraw}
        onComplete={controller.onComplete}
        isMutating={controller.isMutating}
      />
      <Pagination
        page={page.page}
        totalPages={page.totalPages}
        totalItems={page.totalItems}
        onPageChange={controller.setPage}
        itemLabel="ghi danh"
      />
    </>
  );
}
