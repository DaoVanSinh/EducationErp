import { Can, RequirePermission } from "@/entities/permission";
import { useClassAttendanceController } from "@/modules/attendance/hooks/use-class-attendance-controller";
import { AttendanceSessionDialog } from "@/modules/attendance/ui/attendance-session-dialog";
import { AttendanceSheetTable } from "@/modules/attendance/ui/attendance-sheet-table";
import { ACCESS_RULE } from "@/shared/constants/permissions";
import { EmptyState } from "@/shared/ui/empty-state";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassPanel } from "@/shared/ui/glass-panel";
import { PageHeader } from "@/shared/ui/page-header";
import { Skeleton } from "@/shared/ui/skeleton";
import { ClipboardCheck, Users } from "lucide-react";
import type { ReactNode } from "react";
import { useParams } from "react-router-dom";

export function ClassAttendancePage() {
  const { classId = "" } = useParams<{ classId: string }>();
  const controller = useClassAttendanceController(classId);
  const data = controller.attendance.data;

  return (
    <RequirePermission {...ACCESS_RULE.readAttendance}>
      <div className="flex flex-col gap-6">
        <PageHeader
          title="Điểm danh"
          description="Ghi nhận có mặt, đi muộn, vắng của từng buổi học. Vắng có phép không tính vào tỷ lệ chuyên cần."
          actions={
            <Can {...ACCESS_RULE.updateAttendance}>
              <GlassButton size="sm" onClick={controller.openDialog} disabled={!data} icon={<ClipboardCheck size={14} aria-hidden />}>
                Điểm danh buổi học
              </GlassButton>
            </Can>
          }
        />
        {controller.attendance.isError ? <ErrorNotice error={controller.attendance.error} /> : null}
        {controller.attendance.isPending ? <Skeleton className="h-40" /> : null}
        {data ? (
          <GlassPanel className="flex flex-col gap-4">
            <AttendanceBody sessions={data.sessions.length} rows={data.rows.length}>
              <AttendanceSheetTable sessions={data.sessions} rows={data.rows} />
            </AttendanceBody>
          </GlassPanel>
        ) : null}
        {data && controller.isDialogOpen ? (
          <AttendanceSessionDialog classId={classId} attendance={data} open onClose={controller.closeDialog} />
        ) : null}
      </div>
    </RequirePermission>
  );
}

/** Tách các trạng thái rỗng khỏi trang để không rẽ nhánh lồng nhau trong JSX (Mandate #3). */
function AttendanceBody({
  sessions,
  rows,
  children,
}: {
  readonly sessions: number;
  readonly rows: number;
  readonly children: ReactNode;
}) {
  if (rows === 0) {
    return (
      <EmptyState
        icon={<Users size={28} aria-hidden />}
        title="Lớp chưa có học viên nào"
        description="Ghi danh học viên vào lớp này rồi quay lại để điểm danh."
      />
    );
  }
  if (sessions === 0) {
    return (
      <EmptyState
        icon={<ClipboardCheck size={28} aria-hidden />}
        title="Chưa có buổi nào được điểm danh"
        description="Bấm Điểm danh buổi học để ghi nhận buổi đầu tiên."
      />
    );
  }
  return <>{children}</>;
}
