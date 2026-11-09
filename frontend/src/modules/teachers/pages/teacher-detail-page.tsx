import { Can, RequirePermission } from "@/entities/permission";
import { useTeacherDetailController } from "@/modules/teachers/hooks/use-teacher-detail-controller";
import { EditTeacherDialog } from "@/modules/teachers/ui/edit-teacher-dialog";
import { LeaveTeacherDialog } from "@/modules/teachers/ui/leave-teacher-dialog";
import { TeacherBranchHistory } from "@/modules/teachers/ui/teacher-branch-history";
import { TeacherClassesList } from "@/modules/teachers/ui/teacher-classes-list";
import { TeacherPayPanel } from "@/modules/teachers/ui/teacher-pay-panel";
import { ACCESS_RULE } from "@/shared/constants/permissions";
import { formatter } from "@/shared/lib/format";
import { Badge } from "@/shared/ui/badge";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassPanel } from "@/shared/ui/glass-panel";
import { PageHeader } from "@/shared/ui/page-header";
import { SegmentedTabs, type SegmentedTab } from "@/shared/ui/segmented-tabs";
import { Skeleton } from "@/shared/ui/skeleton";
import { BookOpen, CalendarDays, LogOut, Pencil, Wallet } from "lucide-react";
import type { ReactNode } from "react";
import { useParams } from "react-router-dom";
import type { TeacherDetailTab } from "@/modules/teachers/hooks/use-teacher-detail-controller";
import type { TeacherProfileSummary } from "@/entities/teacher";

const TABS: readonly SegmentedTab<TeacherDetailTab>[] = [
  { key: "profile", label: "Hồ sơ", icon: <BookOpen size={16} aria-hidden /> },
  { key: "classes", label: "Lớp", icon: <CalendarDays size={16} aria-hidden /> },
  { key: "pay", label: "Lương", icon: <Wallet size={16} aria-hidden /> },
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
        description={`${teacher.email ?? "—"} · ${teacher.homeBranchName ?? "Chưa thuộc chi nhánh nào"} · ${workStatusText(teacher)}`}
      />

      <SegmentedTabs
        tabs={TABS.filter((tab) => controller.isTabVisible(tab.key))}
        activeTab={controller.activeTab}
        onTabChange={controller.setActiveTab}
      />

      <GlassPanel className="flex flex-col gap-4">
        <TeacherDetailTabContent teacher={teacher} controller={controller} />
      </GlassPanel>

      <EditTeacherDialog teacher={teacher} open={controller.editDialogOpen} onClose={controller.closeEdit} />
      <LeaveTeacherDialog teacher={teacher} open={controller.leaveDialogOpen} onClose={controller.closeLeave} />
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
  if (controller.activeTab === "pay") {
    return <TeacherPayPanel profileId={teacher.id} />;
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
          <Badge tone={teacher.active ? "positive" : "neutral"}>{workStatusText(teacher)}</Badge>
        </Field>
        <Field label="Chi nhánh chủ quản">{teacher.homeBranchName ?? "Chưa thuộc chi nhánh nào"}</Field>
        <Field label="Lịch sử chi nhánh">
          <TeacherBranchHistory profileId={teacher.id} />
        </Field>
      </dl>
      <Can {...ACCESS_RULE.updateTeacher}>
        <div className="flex flex-wrap gap-2">
          <GlassButton
            variant="secondary"
            size="sm"
            onClick={controller.openEdit}
            icon={<Pencil size={14} aria-hidden />}
          >
            Sửa hồ sơ
          </GlassButton>
          {teacher.active ? (
            <GlassButton variant="ghost" size="sm" onClick={controller.openLeave} icon={<LogOut size={14} aria-hidden />}>
              Cho nghỉ
            </GlassButton>
          ) : null}
        </div>
      </Can>
    </div>
  );
}

function workStatusText(teacher: TeacherProfileSummary): string {
  if (teacher.active) {
    return "Đang làm việc";
  }
  return teacher.lastWorkDate === null ? "Đã nghỉ" : `Đã nghỉ từ ${formatter.date(teacher.lastWorkDate)}`;
}

function Field({ label, children }: { readonly label: string; readonly children: ReactNode }) {
  return (
    <div className="glass rounded-2xl p-3">
      <dt className="text-xs text-mist-500">{label}</dt>
      <dd className="text-sm text-mist-100">{children}</dd>
    </div>
  );
}
