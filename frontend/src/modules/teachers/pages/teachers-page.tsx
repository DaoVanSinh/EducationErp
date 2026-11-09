import { Can, RequirePermission } from "@/entities/permission";
import { useTeachersPageController } from "@/modules/teachers/hooks/use-teachers-page-controller";
import { CreateTeacherDialog } from "@/modules/teachers/ui/create-teacher-dialog";
import { EditTeacherDialog } from "@/modules/teachers/ui/edit-teacher-dialog";
import { TeachersTable } from "@/modules/teachers/ui/teachers-table";
import { ACCESS_RULE } from "@/shared/constants/permissions";
import { EmptyState } from "@/shared/ui/empty-state";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassInput } from "@/shared/ui/glass-input";
import { GlassPanel } from "@/shared/ui/glass-panel";
import { PageHeader } from "@/shared/ui/page-header";
import { Pagination } from "@/shared/ui/pagination";
import { Skeleton } from "@/shared/ui/skeleton";
import { GraduationCap, Plus } from "lucide-react";

type Controller = ReturnType<typeof useTeachersPageController>;

export function TeachersPage() {
  const controller = useTeachersPageController();
  const { teachers, editing } = controller;

  return (
    <RequirePermission {...ACCESS_RULE.readTeacher}>
      <div className="flex flex-col gap-6">
        <PageHeader
          title="Giáo viên"
          description="Hồ sơ nghiệp vụ của giáo viên: chi nhánh chủ quản, môn dạy, liên hệ, mức lương. Giáo viên chỉ dạy lớp thuộc chi nhánh chủ quản."
          actions={
            <Can {...ACCESS_RULE.createTeacher}>
              <GlassButton onClick={controller.openCreateDialog} icon={<Plus size={16} aria-hidden />}>
                Thêm giáo viên
              </GlassButton>
            </Can>
          }
        />

        {teachers.isError ? <ErrorNotice error={teachers.error} /> : null}

        <GlassPanel className="flex flex-col gap-4">
          <GlassInput
            aria-label="Tìm theo tên hoặc email"
            placeholder="Tìm theo tên hoặc email"
            value={controller.queryInput}
            onChange={(event) => controller.onQueryChange(event.target.value)}
          />
          {teachers.isPending ? (
            <div className="flex flex-col gap-2">
              <Skeleton className="h-16" />
              <Skeleton className="h-16" />
              <Skeleton className="h-16" />
            </div>
          ) : null}
          <TeachersList controller={controller} />
        </GlassPanel>

        <CreateTeacherDialog open={controller.createDialogOpen} onClose={controller.closeCreateDialog} />

        {editing === null ? null : (
          <EditTeacherDialog key={editing.id} teacher={editing} open onClose={controller.closeEdit} />
        )}
      </div>
    </RequirePermission>
  );
}

function TeachersList({ controller }: { readonly controller: Controller }) {
  const data = controller.teachers.data;
  if (!data) {
    return null;
  }
  if (data.items.length === 0) {
    return (
      <EmptyState
        icon={<GraduationCap size={28} aria-hidden />}
        title="Không có giáo viên phù hợp"
        description="Thêm giáo viên mới - hệ thống tạo luôn tài khoản đăng nhập và gửi email mời."
      />
    );
  }
  return (
    <>
      <TeachersTable rows={data.items} onEdit={controller.setEditing} />
      <Pagination
        page={data.page}
        totalPages={data.totalPages}
        totalItems={data.totalItems}
        onPageChange={controller.setPage}
        itemLabel="hồ sơ"
      />
    </>
  );
}
