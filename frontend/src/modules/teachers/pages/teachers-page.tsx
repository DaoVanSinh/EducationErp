import { useTeachers, type TeacherProfileSummary } from "@/entities/teacher";
import { Can, RequirePermission } from "@/entities/permission";
import { CreateTeacherDialog } from "@/modules/teachers/ui/create-teacher-dialog";
import { EditTeacherDialog } from "@/modules/teachers/ui/edit-teacher-dialog";
import { TeachersTable } from "@/modules/teachers/ui/teachers-table";
import { ACCESS_RULE } from "@/shared/constants/permissions";
import { DEFAULT_PAGE_SIZE } from "@/shared/constants/query-config";
import { EmptyState } from "@/shared/ui/empty-state";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassPanel } from "@/shared/ui/glass-panel";
import { PageHeader } from "@/shared/ui/page-header";
import { Pagination } from "@/shared/ui/pagination";
import { Skeleton } from "@/shared/ui/skeleton";
import { GraduationCap, Plus } from "lucide-react";
import { useState } from "react";

export function TeachersPage() {
  const [page, setPage] = useState(0);
  const [createDialogOpen, setCreateDialogOpen] = useState(false);
  const [editing, setEditing] = useState<TeacherProfileSummary | null>(null);
  const teachers = useTeachers(page, DEFAULT_PAGE_SIZE);

  return (
    <RequirePermission {...ACCESS_RULE.readTeacher}>
      <div className="flex flex-col gap-6">
        <PageHeader
          title="Giáo viên"
          description="Hồ sơ nghiệp vụ của giáo viên — môn dạy, liên hệ."
          actions={
            <Can {...ACCESS_RULE.createTeacher}>
              <GlassButton onClick={() => setCreateDialogOpen(true)} icon={<Plus size={16} aria-hidden />}>
                Hồ sơ mới
              </GlassButton>
            </Can>
          }
        />

        {teachers.isError ? <ErrorNotice error={teachers.error} /> : null}

        <GlassPanel className="flex flex-col gap-4">
          {teachers.isPending ? (
            <div className="flex flex-col gap-2">
              <Skeleton className="h-16" />
              <Skeleton className="h-16" />
              <Skeleton className="h-16" />
            </div>
          ) : null}

          {teachers.data ? (
            teachers.data.items.length === 0 ? (
              <EmptyState
                icon={<GraduationCap size={28} aria-hidden />}
                title="Chưa có hồ sơ giáo viên nào"
                description="Tạo tài khoản giáo viên ở trang Tài khoản trước, rồi tạo hồ sơ ở đây."
              />
            ) : (
              <>
                <TeachersTable rows={teachers.data.items} onEdit={setEditing} />
                <Pagination
                  page={teachers.data.page}
                  totalPages={teachers.data.totalPages}
                  totalItems={teachers.data.totalItems}
                  onPageChange={setPage}
                  itemLabel="hồ sơ"
                />
              </>
            )
          ) : null}
        </GlassPanel>

        <CreateTeacherDialog open={createDialogOpen} onClose={() => setCreateDialogOpen(false)} />

        {editing === null ? null : (
          <EditTeacherDialog key={editing.id} teacher={editing} open={editing !== null} onClose={() => setEditing(null)} />
        )}
      </div>
    </RequirePermission>
  );
}
