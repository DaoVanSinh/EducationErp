import { useCourses, type CourseSummary } from "@/entities/course";
import { Can, RequirePermission } from "@/entities/permission";
import { CoursesTable } from "@/modules/courses/ui/courses-table";
import { CreateCourseDialog } from "@/modules/courses/ui/create-course-dialog";
import { EditCourseDialog } from "@/modules/courses/ui/edit-course-dialog";
import { ACCESS_RULE } from "@/shared/constants/permissions";
import { DEFAULT_PAGE_SIZE } from "@/shared/constants/query-config";
import { EmptyState } from "@/shared/ui/empty-state";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassPanel } from "@/shared/ui/glass-panel";
import { PageHeader } from "@/shared/ui/page-header";
import { Pagination } from "@/shared/ui/pagination";
import { Skeleton } from "@/shared/ui/skeleton";
import { BookOpen, Plus } from "lucide-react";
import { useState } from "react";

export function CoursesPage() {
  const [page, setPage] = useState(0);
  const [createDialogOpen, setCreateDialogOpen] = useState(false);
  const [editing, setEditing] = useState<CourseSummary | null>(null);
  const courses = useCourses(page, DEFAULT_PAGE_SIZE);

  return (
    <RequirePermission {...ACCESS_RULE.readCourse}>
      <div className="flex flex-col gap-6">
        <PageHeader
          title="Khóa học"
          description="Danh mục khóa học dùng chung toàn tổ chức. Khóa học chỉ được vô hiệu hoá, không xoá."
          actions={
            <Can {...ACCESS_RULE.createCourse}>
              <GlassButton onClick={() => setCreateDialogOpen(true)} icon={<Plus size={16} aria-hidden />}>
                Khóa học mới
              </GlassButton>
            </Can>
          }
        />

        {courses.isError ? <ErrorNotice error={courses.error} /> : null}

        <GlassPanel className="flex flex-col gap-4">
          {courses.isPending ? (
            <div className="flex flex-col gap-2">
              <Skeleton className="h-16" />
              <Skeleton className="h-16" />
              <Skeleton className="h-16" />
            </div>
          ) : null}

          {courses.data ? (
            courses.data.items.length === 0 ? (
              <EmptyState
                icon={<BookOpen size={28} aria-hidden />}
                title="Chưa có khóa học nào"
                description="Tạo khóa học đầu tiên để bắt đầu mở lớp."
              />
            ) : (
              <>
                <CoursesTable rows={courses.data.items} onEdit={setEditing} />
                <Pagination
                  page={courses.data.page}
                  totalPages={courses.data.totalPages}
                  totalItems={courses.data.totalItems}
                  onPageChange={setPage}
                  itemLabel="khóa học"
                />
              </>
            )
          ) : null}
        </GlassPanel>

        <CreateCourseDialog open={createDialogOpen} onClose={() => setCreateDialogOpen(false)} />

        {editing === null ? null : (
          <EditCourseDialog key={editing.id} course={editing} open={editing !== null} onClose={() => setEditing(null)} />
        )}
      </div>
    </RequirePermission>
  );
}
