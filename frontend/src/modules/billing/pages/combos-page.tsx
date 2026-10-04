import { Can, RequirePermission } from "@/entities/permission";
import { useCombosPageController } from "@/modules/billing/hooks/use-combos-page-controller";
import { CombosTable } from "@/modules/billing/ui/combos-table";
import { CreateComboDialog } from "@/modules/billing/ui/create-combo-dialog";
import { ACCESS_RULE } from "@/shared/constants/permissions";
import { EmptyState } from "@/shared/ui/empty-state";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassPanel } from "@/shared/ui/glass-panel";
import { GlassSelect } from "@/shared/ui/glass-select";
import { PageHeader } from "@/shared/ui/page-header";
import { Pagination } from "@/shared/ui/pagination";
import { Skeleton } from "@/shared/ui/skeleton";
import { Package, Plus } from "lucide-react";

export function CombosPage() {
  const controller = useCombosPageController();

  return (
    <RequirePermission {...ACCESS_RULE.readInvoice}>
      <div className="flex flex-col gap-6">
        <PageHeader
          title="Combo khoá học"
          description="Gộp các khoá đang học của một học viên để giảm giá theo số khoá. Combo thu tối đa 3 đợt, một hạn đóng chung."
          actions={
            <Can {...ACCESS_RULE.createInvoice}>
              <GlassButton onClick={controller.openCreateDialog} icon={<Plus size={16} aria-hidden />}>
                Tạo combo
              </GlassButton>
            </Can>
          }
        />

        {controller.combos.isError ? <ErrorNotice error={controller.combos.error} /> : null}

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
          </div>

          {controller.combos.isPending ? (
            <div className="flex flex-col gap-2">
              <Skeleton className="h-16" />
              <Skeleton className="h-16" />
            </div>
          ) : null}

          {controller.combos.data ? <CombosListSection controller={controller} /> : null}
        </GlassPanel>

        <CreateComboDialog open={controller.createDialogOpen} onClose={controller.closeCreateDialog} />
      </div>
    </RequirePermission>
  );
}

/** Tách nhánh rỗng/có dữ liệu ra component riêng - tránh nested ternary trong JSX (Mandate #3). */
function CombosListSection({
  controller,
}: {
  readonly controller: ReturnType<typeof useCombosPageController>;
}) {
  const page = controller.combos.data;
  if (!page || page.items.length === 0) {
    return (
      <EmptyState
        icon={<Package size={28} aria-hidden />}
        title="Chưa có combo nào"
        description="Ghi danh học viên vào nhiều khoá trước, rồi gộp chúng thành một combo giảm giá."
      />
    );
  }
  return (
    <>
      <CombosTable rows={page.items} />
      <Pagination
        page={page.page}
        totalPages={page.totalPages}
        totalItems={page.totalItems}
        onPageChange={controller.setPage}
        itemLabel="combo"
      />
    </>
  );
}
