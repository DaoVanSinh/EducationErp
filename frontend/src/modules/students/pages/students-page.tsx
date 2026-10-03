import { useStudents, type StudentProfileSummary } from "@/entities/student";
import { Can, RequirePermission } from "@/entities/permission";
import { CreateStudentDialog } from "@/modules/students/ui/create-student-dialog";
import { EditStudentDialog } from "@/modules/students/ui/edit-student-dialog";
import { StudentsTable } from "@/modules/students/ui/students-table";
import { ACCESS_RULE } from "@/shared/constants/permissions";
import { DEFAULT_PAGE_SIZE } from "@/shared/constants/query-config";
import { EmptyState } from "@/shared/ui/empty-state";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassPanel } from "@/shared/ui/glass-panel";
import { PageHeader } from "@/shared/ui/page-header";
import { Pagination } from "@/shared/ui/pagination";
import { Skeleton } from "@/shared/ui/skeleton";
import { Plus, Users } from "lucide-react";
import { useState } from "react";

export function StudentsPage() {
  const [page, setPage] = useState(0);
  const [createDialogOpen, setCreateDialogOpen] = useState(false);
  const [editing, setEditing] = useState<StudentProfileSummary | null>(null);
  const students = useStudents(page, DEFAULT_PAGE_SIZE);

  return (
    <RequirePermission {...ACCESS_RULE.readStudent}>
      <div className="flex flex-col gap-6">
        <PageHeader
          title="Học viên"
          description="Hồ sơ nghiệp vụ của học viên."
          actions={
            <Can {...ACCESS_RULE.createStudent}>
              <GlassButton onClick={() => setCreateDialogOpen(true)} icon={<Plus size={16} aria-hidden />}>
                Hồ sơ mới
              </GlassButton>
            </Can>
          }
        />

        {students.isError ? <ErrorNotice error={students.error} /> : null}

        <GlassPanel className="flex flex-col gap-4">
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
                description="Tạo tài khoản học viên ở trang Tài khoản trước, rồi tạo hồ sơ ở đây."
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
