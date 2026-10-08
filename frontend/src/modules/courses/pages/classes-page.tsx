import { useClasses, type ClassSummary } from "@/entities/class";
import { Can, RequirePermission } from "@/entities/permission";
import { ClassesTable } from "@/modules/courses/ui/classes-table";
import { CreateClassDialog } from "@/modules/courses/ui/create-class-dialog";
import { EditClassDialog } from "@/modules/courses/ui/edit-class-dialog";
import { ACCESS_RULE } from "@/shared/constants/permissions";
import { DEFAULT_PAGE_SIZE } from "@/shared/constants/query-config";
import { EmptyState } from "@/shared/ui/empty-state";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassPanel } from "@/shared/ui/glass-panel";
import { PageHeader } from "@/shared/ui/page-header";
import { Pagination } from "@/shared/ui/pagination";
import { Skeleton } from "@/shared/ui/skeleton";
import { CalendarDays, Plus } from "lucide-react";
import { useState } from "react";

export function ClassesPage() {
  const [page, setPage] = useState(0);
  const [createDialogOpen, setCreateDialogOpen] = useState(false);
  const [editing, setEditing] = useState<ClassSummary | null>(null);
  const classes = useClasses(page, DEFAULT_PAGE_SIZE);

  return (
    <RequirePermission {...ACCESS_RULE.readClass}>
      <div className="flex flex-col gap-6">
        <PageHeader
          title="Lớp học"
          description="Danh sách lớp đã mở. Lớp chỉ được vô hiệu hoá, không xoá."
          actions={
            <Can {...ACCESS_RULE.createClass}>
              <GlassButton onClick={() => setCreateDialogOpen(true)} icon={<Plus size={16} aria-hidden />}>
                Mở lớp mới
              </GlassButton>
            </Can>
          }
        />

        {classes.isError ? <ErrorNotice error={classes.error} /> : null}

        <GlassPanel className="flex flex-col gap-4">
          {classes.isPending ? (
            <div className="flex flex-col gap-2">
              <Skeleton className="h-16" />
              <Skeleton className="h-16" />
              <Skeleton className="h-16" />
            </div>
          ) : null}

          {classes.data ? (
            classes.data.items.length === 0 ? (
              <EmptyState
                icon={<CalendarDays size={28} aria-hidden />}
                title="Chưa có lớp nào"
                description="Mở lớp đầu tiên từ một khóa học đã có."
              />
            ) : (
              <>
                <ClassesTable rows={classes.data.items} onEdit={setEditing} />
                <Pagination
                  page={classes.data.page}
                  totalPages={classes.data.totalPages}
                  totalItems={classes.data.totalItems}
                  onPageChange={setPage}
                  itemLabel="lớp"
                />
              </>
            )
          ) : null}
        </GlassPanel>

        <CreateClassDialog open={createDialogOpen} onClose={() => setCreateDialogOpen(false)} />

        {editing === null ? null : (
          <EditClassDialog key={editing.id} cls={editing} open={editing !== null} onClose={() => setEditing(null)} />
        )}
      </div>
    </RequirePermission>
  );
}
