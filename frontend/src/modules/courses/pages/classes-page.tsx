import { Can, RequirePermission } from "@/entities/permission";
import { TRANSITION_COPY, useClassesPageController } from "@/modules/courses/hooks/use-classes-page-controller";
import { ClassTransitionDescription } from "@/modules/courses/ui/class-transition-description";
import { ClassesTable } from "@/modules/courses/ui/classes-table";
import { CreateClassDialog } from "@/modules/courses/ui/create-class-dialog";
import { EditClassDialog } from "@/modules/courses/ui/edit-class-dialog";
import { ACCESS_RULE } from "@/shared/constants/permissions";
import { ConfirmDialog } from "@/shared/ui/confirm-dialog";
import { EmptyState } from "@/shared/ui/empty-state";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassPanel } from "@/shared/ui/glass-panel";
import { PageHeader } from "@/shared/ui/page-header";
import { Pagination } from "@/shared/ui/pagination";
import { Skeleton } from "@/shared/ui/skeleton";
import { CalendarDays, Plus } from "lucide-react";

export function ClassesPage() {
  const controller = useClassesPageController();
  const { classes, pending } = controller;

  return (
    <RequirePermission {...ACCESS_RULE.readClass}>
      <div className="flex flex-col gap-6">
        <PageHeader
          title="Lớp học"
          description="Lớp mở ở trạng thái Đang tuyển sinh, tự khai giảng khi tới ngày và đủ sĩ số tối thiểu."
          actions={
            <Can {...ACCESS_RULE.createClass}>
              <GlassButton onClick={controller.openCreateDialog} icon={<Plus size={16} aria-hidden />}>
                Mở lớp mới
              </GlassButton>
            </Can>
          }
        />

        {classes.isError ? <ErrorNotice error={classes.error} /> : null}

        <GlassPanel className="flex flex-col gap-4">
          {classes.isPending ? (
            <div className="flex flex-col gap-2">
              <Skeleton className="h-20" />
              <Skeleton className="h-20" />
              <Skeleton className="h-20" />
            </div>
          ) : null}
          {classes.data && classes.data.items.length === 0 ? (
            <EmptyState
              icon={<CalendarDays size={28} aria-hidden />}
              title="Chưa có lớp nào"
              description="Mở lớp đầu tiên từ một khóa học đã có."
            />
          ) : null}
          {classes.data && classes.data.items.length > 0 ? (
            <>
              <ClassesTable
                rows={classes.data.items}
                today={controller.today}
                onEdit={controller.startEditing}
                onTransition={controller.requestTransition}
              />
              <Pagination
                page={classes.data.page}
                totalPages={classes.data.totalPages}
                totalItems={classes.data.totalItems}
                onPageChange={controller.setPage}
                itemLabel="lớp"
              />
            </>
          ) : null}
        </GlassPanel>

        <CreateClassDialog open={controller.createDialogOpen} onClose={controller.closeCreateDialog} />

        {controller.editing === null ? null : (
          <EditClassDialog
            key={controller.editing.id}
            cls={controller.editing}
            open={controller.editing !== null}
            onClose={controller.stopEditing}
          />
        )}

        {pending === null ? null : (
          <ConfirmDialog
            open
            title={`${TRANSITION_COPY[pending.transition].title} ${pending.cls.code}`}
            description={<ClassTransitionDescription cls={pending.cls} transition={pending.transition} />}
            confirmLabel={TRANSITION_COPY[pending.transition].confirmLabel}
            tone={TRANSITION_COPY[pending.transition].tone}
            isPending={controller.isTransitioning}
            error={controller.transitionError}
            onConfirm={controller.confirmTransition}
            onClose={controller.closeTransition}
          />
        )}
      </div>
    </RequirePermission>
  );
}
