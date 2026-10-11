import { RequirePermission } from "@/entities/permission";
import { useTeacherDetailController } from "@/modules/teachers/hooks/use-teacher-detail-controller";
import { EditTeacherDialog } from "@/modules/teachers/ui/edit-teacher-dialog";
import { TeacherClassesList } from "@/modules/teachers/ui/teacher-classes-list";
import { TeacherContractsList } from "@/modules/teachers/ui/teacher-contracts-list";
import { ACCESS_RULE } from "@/shared/constants/permissions";
import { Badge } from "@/shared/ui/badge";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassPanel } from "@/shared/ui/glass-panel";
import { PageHeader } from "@/shared/ui/page-header";
import { SegmentedTabs, type SegmentedTab } from "@/shared/ui/segmented-tabs";
import { Skeleton } from "@/shared/ui/skeleton";
import { BookOpen, CalendarDays, FileSignature, Pencil } from "lucide-react";
import type { ReactNode } from "react";
import { useParams } from "react-router-dom";
import type { TeacherDetailTab } from "@/modules/teachers/hooks/use-teacher-detail-controller";
import type { TeacherProfileSummary } from "@/entities/teacher";

const TABS: readonly SegmentedTab<TeacherDetailTab>[] = [
  { key: "profile", label: "Hồ sơ", icon: <BookOpen size={16} aria-hidden /> },
  { key: "classes", label: "Lớp đang dạy", icon: <CalendarDays size={16} aria-hidden /> },
  { key: "contracts", label: "Hợp đồng lao động", icon: <FileSignature size={16} aria-hidden /> },
];

export function TeacherDetailPage() {
  const { profileId = "" } = useParams<{ profileId: string }>();
  const controller = useTeacherDetailController(profileId);

  return (
    <RequirePermission {...ACCESS_RULE.readTeacher}>
      <div className="flex flex-col gap-6">
        {controller.teacher.isError ? <ErrorNotice error={controller.teacher.error} /> : null}
        {controller.teacher.isPending ? <Skeleton className="h-24" /> : null}

        {controller.teacher.data ? (
          <TeacherDetailBody teacher={controller.teacher.data} controller={controller} />
        ) : null}
      </div>
    </RequirePermission>
  );
}

/** Tách ra để trang không rẽ nhánh trong JSX (Mandate #3). */
function TeacherDetailBody({
  teacher,
  controller,
}: {
  readonly teacher: TeacherProfileSummary;
  readonly controller: ReturnType<typeof useTeacherDetailController>;
}) {
  return (
    <>
      <PageHeader
        title={teacher.fullName ?? "Chưa rõ tên"}
        description={`${teacher.email ?? "—"} · ${teacher.active ? "Đang hoạt động" : "Đã vô hiệu hoá"}`}
      />

      <SegmentedTabs tabs={TABS} activeTab={controller.activeTab} onTabChange={controller.setActiveTab} />

      <GlassPanel className="flex flex-col gap-4">
        <TeacherDetailTabContent teacher={teacher} controller={controller} />
      </GlassPanel>

      <EditTeacherDialog teacher={teacher} open={controller.editDialogOpen} onClose={controller.closeEdit} />
    </>
  );
}

function TeacherDetailTabContent({
  teacher,
  controller,
}: {
  readonly teacher: TeacherProfileSummary;
  readonly controller: ReturnType<typeof useTeacherDetailController>;
}) {
  if (controller.activeTab === "classes") {
    return <TeacherClassesList teacherAccountId={teacher.accountId} />;
  }
  if (controller.activeTab === "contracts") {
    return <TeacherContractsList accountId={teacher.accountId} />;
  }
  return (
    <div className="flex flex-col gap-4">
      <dl className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <Field label="Môn/chuyên môn dạy">
          {teacher.subjects.length === 0 ? "Chưa có môn dạy" : teacher.subjects.join(", ")}
        </Field>
        <Field label="Số điện thoại">{teacher.phone ?? "—"}</Field>
        <Field label="Giới thiệu ngắn">{teacher.bio ?? "—"}</Field>
        <Field label="Trạng thái">
          <Badge tone={teacher.active ? "positive" : "neutral"}>
            {teacher.active ? "Đang hoạt động" : "Đã vô hiệu hoá"}
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
