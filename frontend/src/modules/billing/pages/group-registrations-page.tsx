import { Can, RequirePermission } from "@/entities/permission";
import { useGroupRegistrationsPageController } from "@/modules/billing/hooks/use-group-registrations-page-controller";
import { CreateGroupRegistrationDialog } from "@/modules/billing/ui/create-group-registration-dialog";
import { GroupRegistrationsTable } from "@/modules/billing/ui/group-registrations-table";
import { ACCESS_RULE } from "@/shared/constants/permissions";
import { ConfirmDialog } from "@/shared/ui/confirm-dialog";
import { EmptyState } from "@/shared/ui/empty-state";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassPanel } from "@/shared/ui/glass-panel";
import { GlassSelect } from "@/shared/ui/glass-select";
import { PageHeader } from "@/shared/ui/page-header";
import { Pagination } from "@/shared/ui/pagination";
import { Skeleton } from "@/shared/ui/skeleton";
import { Plus, Users } from "lucide-react";

type Controller = ReturnType<typeof useGroupRegistrationsPageController>;

export function GroupRegistrationsPage() {
  const controller = useGroupRegistrationsPageController();

  return (
    <RequirePermission {...ACCESS_RULE.readInvoice}>
      <div className="flex flex-col gap-6">
        <PageHeader
          title="Đăng ký nhóm"
          description="Nhiều học viên rủ nhau học chung một lớp được giảm theo số người. Mỗi người vẫn tự đóng học phí của mình, tối đa 3 đợt."
          actions={
            <Can {...ACCESS_RULE.createInvoice}>
              <GlassButton onClick={controller.openCreateDialog} icon={<Plus size={16} aria-hidden />}>
                Lập nhóm
              </GlassButton>
            </Can>
          }
        />

        {controller.groups.isError ? <ErrorNotice error={controller.groups.error} /> : null}

        <GlassPanel className="flex flex-col gap-4">
          <GlassSelect
            aria-label="Lọc theo khoá học"
            value={controller.courseFilter}
            onChange={(event) => controller.onSelectCourse(event.target.value)}
          >
            <option value="">Mọi khoá học</option>
            {controller.courseOptions.map((course) => (
              <option key={course.id} value={course.id}>
                {course.code} · {course.name}
              </option>
            ))}
          </GlassSelect>
          <GroupsListSection controller={controller} />
        </GlassPanel>

        <CreateGroupRegistrationDialog open={controller.createDialogOpen} onClose={controller.closeCreateDialog} />
        <ConfirmDialog
          open={controller.cancelTarget !== null}
          title="Huỷ nhóm học chung?"
          description="Học phí của các thành viên trở về giá niêm yết. Chỉ huỷ được khi chưa ai trong nhóm được phát hành hoá đơn."
          confirmLabel="Huỷ nhóm"
          tone="danger"
          isPending={controller.isCancelling}
          error={controller.cancelError}
          onConfirm={controller.confirmCancel}
          onClose={controller.closeCancel}
        />
      </div>
    </RequirePermission>
  );
}

/** Tách nhánh tải/rỗng/có dữ liệu ra component riêng - tránh nested ternary trong JSX (Mandate #3). */
function GroupsListSection({ controller }: { readonly controller: Controller }) {
  if (controller.groups.isPending) {
    return <Skeleton className="h-16" />;
  }
  const page = controller.pageInfo;
  if (!page || page.items.length === 0) {
    return (
      <EmptyState
        icon={<Users size={28} aria-hidden />}
        title="Chưa có nhóm nào"
        description="Ghi danh các bạn vào cùng một lớp trước, rồi lập nhóm để mỗi người được giảm học phí."
      />
    );
  }
  return (
    <>
      <GroupRegistrationsTable rows={controller.rows} onCancel={controller.askCancel} />
      <Pagination
        page={page.page}
        totalPages={page.totalPages}
        totalItems={page.totalItems}
        onPageChange={controller.setPage}
        itemLabel="nhóm"
      />
    </>
  );
}
