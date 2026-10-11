import { Can, RequirePermission } from "@/entities/permission";
import type { StudentProfileSummary } from "@/entities/student";
import { useStudentDetailController } from "@/modules/students/hooks/use-student-detail-controller";
import { EditStudentDialog } from "@/modules/students/ui/edit-student-dialog";
import { StudentAcademicSummaryList } from "@/modules/students/ui/student-academic-summary-list";
import { StudentCombosList } from "@/modules/students/ui/student-combos-list";
import { StudentEnrollmentsList } from "@/modules/students/ui/student-enrollments-list";
import { StudentInvoicesList } from "@/modules/students/ui/student-invoices-list";
import { ACCESS_RULE } from "@/shared/constants/permissions";
import { Badge } from "@/shared/ui/badge";
import { EmptyState } from "@/shared/ui/empty-state";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassPanel } from "@/shared/ui/glass-panel";
import { PageHeader } from "@/shared/ui/page-header";
import { SegmentedTabs, type SegmentedTab } from "@/shared/ui/segmented-tabs";
import { Skeleton } from "@/shared/ui/skeleton";
import { ClipboardList, GraduationCap, Package, Pencil, Receipt, ShieldOff, UserCircle } from "lucide-react";
import type { ReactNode } from "react";
import { useParams } from "react-router-dom";
import type { StudentDetailTab } from "@/modules/students/hooks/use-student-detail-controller";

const TABS: readonly SegmentedTab<StudentDetailTab>[] = [
  { key: "profile", label: "Hồ sơ", icon: <UserCircle size={16} aria-hidden /> },
  { key: "enrollments", label: "Ghi danh", icon: <ClipboardList size={16} aria-hidden /> },
  { key: "invoices", label: "Hoá đơn học phí", icon: <Receipt size={16} aria-hidden /> },
  { key: "combos", label: "Combo", icon: <Package size={16} aria-hidden /> },
  { key: "academic", label: "Kết quả học tập", icon: <GraduationCap size={16} aria-hidden /> },
];

export function StudentDetailPage() {
  const { profileId = "" } = useParams<{ profileId: string }>();
  const controller = useStudentDetailController(profileId);

  return (
    <RequirePermission {...ACCESS_RULE.readStudent}>
      <div className="flex flex-col gap-6">
        {controller.student.isError ? <ErrorNotice error={controller.student.error} /> : null}
        {controller.student.isPending ? <Skeleton className="h-24" /> : null}

        {controller.student.data ? (
          <StudentDetailBody student={controller.student.data} controller={controller} />
        ) : null}
      </div>
    </RequirePermission>
  );
}

/** Tách ra để trang không rẽ nhánh trong JSX (Mandate #3). */
function StudentDetailBody({
  student,
  controller,
}: {
  readonly student: StudentProfileSummary;
  readonly controller: ReturnType<typeof useStudentDetailController>;
}) {
  return (
    <>
      <PageHeader
        title={student.fullName ?? "Chưa rõ tên"}
        description={`${student.email ?? "—"} · ${student.active ? "Đang hoạt động" : "Đã vô hiệu hoá"}`}
      />

      <SegmentedTabs tabs={TABS} activeTab={controller.activeTab} onTabChange={controller.setActiveTab} />

      <GlassPanel className="flex flex-col gap-4">
        <StudentDetailTabContent student={student} controller={controller} />
      </GlassPanel>

      <EditStudentDialog student={student} open={controller.editDialogOpen} onClose={controller.closeEdit} />
    </>
  );
}

function StudentDetailTabContent({
  student,
  controller,
}: {
  readonly student: StudentProfileSummary;
  readonly controller: ReturnType<typeof useStudentDetailController>;
}) {
  if (controller.activeTab === "enrollments") {
    return <StudentEnrollmentsList studentProfileId={student.id} />;
  }
  if (controller.activeTab === "invoices") {
    return <StudentInvoicesList studentProfileId={student.id} />;
  }
  if (controller.activeTab === "combos") {
    return <StudentCombosList studentProfileId={student.id} />;
  }
  if (controller.activeTab === "academic") {
    return (
      <Can
        {...ACCESS_RULE.readGrade}
        fallback={
          <EmptyState
            icon={<ShieldOff size={28} aria-hidden />}
            title="Bạn không có quyền xem phần này"
            description="Chỉ giáo viên của lớp hoặc quản trị viên mới xem được kết quả học tập."
          />
        }
      >
        <StudentAcademicSummaryList studentProfileId={student.id} />
      </Can>
    );
  }
  return (
    <div className="flex flex-col gap-4">
      <dl className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <Field label="Ngày sinh">{student.dateOfBirth ?? "—"}</Field>
        <Field label="Số điện thoại">{student.phone ?? "—"}</Field>
        <Field label="Biết đến trung tâm qua">{student.sourceChannel ?? "—"}</Field>
        <Field label="Trạng thái">
          <Badge tone={student.active ? "positive" : "neutral"}>
            {student.active ? "Đang hoạt động" : "Đã vô hiệu hoá"}
          </Badge>
        </Field>
      </dl>
      <div>
        <GlassButton
          variant="secondary"
          size="sm"
          onClick={controller.openEdit}
          icon={<Pencil size={14} aria-hidden />}
        >
          Sửa hồ sơ
        </GlassButton>
      </div>
    </div>
  );
}

function Field({ label, children }: { readonly label: string; readonly children: ReactNode }) {
  return (
    <div className="glass rounded-2xl p-3">
      <dt className="text-xs text-mist-500">{label}</dt>
      <dd className="text-sm text-mist-100">{children}</dd>
    </div>
  );
}
