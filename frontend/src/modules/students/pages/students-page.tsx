import { Can, RequirePermission } from "@/entities/permission";
import { useStudentsPageController } from "@/modules/students/hooks/use-students-page-controller";
import { CreateStudentDialog } from "@/modules/students/ui/create-student-dialog";
import { EditStudentDialog } from "@/modules/students/ui/edit-student-dialog";
import { StudentsTable } from "@/modules/students/ui/students-table";
import { ACCESS_RULE } from "@/shared/constants/permissions";
import { EmptyState } from "@/shared/ui/empty-state";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassInput } from "@/shared/ui/glass-input";
import { GlassPanel } from "@/shared/ui/glass-panel";
import { PageHeader } from "@/shared/ui/page-header";
import { Pagination } from "@/shared/ui/pagination";
import { Skeleton } from "@/shared/ui/skeleton";
import { Plus, Users } from "lucide-react";

export function StudentsPage() {
  const { students, setPage, createDialogOpen, setCreateDialogOpen, editing, setEditing, queryInput, onQueryChange } =
    useStudentsPageController();

  return (
    <RequirePermission {...ACCESS_RULE.readStudent}>
      <div className="flex flex-col gap-6">
        <PageHeader
          title="Học viên"
          description="Hồ sơ học viên kèm tài khoản đăng nhập cổng học tập. Khi chọn một cơ sở: học viên có cơ sở chính ở đó hoặc đang học lớp ở đó."
          actions={
            <Can {...ACCESS_RULE.createStudent}>
              <GlassButton onClick={() => setCreateDialogOpen(true)} icon={<Plus size={16} aria-hidden />}>
                Thêm học viên
              </GlassButton>
            </Can>
          }
        />

        {students.isError ? <ErrorNotice error={students.error} /> : null}

        <GlassPanel className="flex flex-col gap-4">
          <GlassInput
            aria-label="Tìm theo tên hoặc email"
            placeholder="Tìm theo tên hoặc email"
            value={queryInput}
            onChange={(event) => onQueryChange(event.target.value)}
          />
          {students.isPending ? (
            <div className="flex flex-col gap-2">
              <Skeleton className="h-16" />
              <Skeleton className="h-16" />
              <Skeleton className="h-16" />
            </div>
          ) : null}

          {students.data ? (
            students.data.items.length === 0 ? (
              <EmptyState
                icon={<Users size={28} aria-hidden />}
                title="Chưa có hồ sơ học viên nào"
                description="Thêm học viên mới - hệ thống tạo luôn tài khoản đăng nhập và gửi email mời."
              />
            ) : (
              <>
                <StudentsTable rows={students.data.items} onEdit={setEditing} />
                <Pagination
                  page={students.data.page}
                  totalPages={students.data.totalPages}
                  totalItems={students.data.totalItems}
                  onPageChange={setPage}
                  itemLabel="hồ sơ"
                />
              </>
            )
          ) : null}
        </GlassPanel>

        <CreateStudentDialog open={createDialogOpen} onClose={() => setCreateDialogOpen(false)} />

        {editing === null ? null : (
          <EditStudentDialog key={editing.id} student={editing} open={editing !== null} onClose={() => setEditing(null)} />
        )}
      </div>
    </RequirePermission>
  );
}
