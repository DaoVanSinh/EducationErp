import {
  ATTENDANCE_STATUS,
  ATTENDANCE_STATUS_LABEL,
  ATTENDANCE_STATUS_ORDER,
  type AttendanceStatus,
  type ClassAttendance,
} from "@/entities/attendance";
import { useAttendanceSessionForm } from "@/modules/attendance/hooks/use-attendance-session-form";
import { cx } from "@/shared/lib/class-names";
import { EmptyState } from "@/shared/ui/empty-state";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { FormField } from "@/shared/ui/form-field";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassInput } from "@/shared/ui/glass-input";
import { GlassModal } from "@/shared/ui/glass-modal";
import { UserX } from "lucide-react";

export interface AttendanceSessionDialogProps {
  readonly classId: string;
  readonly attendance: ClassAttendance;
  readonly open: boolean;
  readonly onClose: () => void;
}

/** Điểm danh một buổi: chọn ngày (đã điểm danh thì nạp lại để sửa), chọn trạng thái từng học viên. */
export function AttendanceSessionDialog({ classId, attendance, open, onClose }: AttendanceSessionDialogProps) {
  const form = useAttendanceSessionForm(classId, attendance, onClose);

  return (
    <GlassModal
      open={open}
      onClose={onClose}
      title="Điểm danh buổi học"
      description="Mặc định mọi học viên có mặt - chỉ cần đổi những người vắng hoặc đi muộn."
    >
      <form id="attendance-session-form" onSubmit={form.handleSubmit} className="flex flex-col gap-4" noValidate>
        {form.submitError ? <ErrorNotice error={form.submitError} /> : null}
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <FormField label="Ngày học" htmlFor="attendance-date">
            <GlassInput
              id="attendance-date"
              type="date"
              max={form.maxDate}
              value={form.sessionDate}
              onChange={(event) => form.changeDate(event.target.value)}
            />
          </FormField>
          <FormField label="Ghi chú buổi học" htmlFor="attendance-note">
            <GlassInput id="attendance-note" value={form.note} onChange={(event) => form.setNote(event.target.value)} />
          </FormField>
        </div>
        <StudentStatusList form={form} />
      </form>
      <footer className="flex justify-end gap-2">
        <GlassButton variant="ghost" onClick={onClose}>
          Huỷ
        </GlassButton>
        <GlassButton
          type="submit"
          form="attendance-session-form"
          loading={form.isSubmitting}
          disabled={!form.hasActiveStudents}
        >
          Lưu điểm danh
        </GlassButton>
      </footer>
    </GlassModal>
  );
}

/** Danh sách học viên đang học kèm bộ chọn trạng thái; lớp không còn ai đang học thì báo rỗng. */
function StudentStatusList({ form }: { readonly form: ReturnType<typeof useAttendanceSessionForm> }) {
  if (!form.hasActiveStudents) {
    return (
      <EmptyState
        icon={<UserX size={28} aria-hidden />}
        title="Lớp không còn học viên đang học"
        description="Học viên đã hoàn tất hoặc đã rút khỏi lớp nên không cần điểm danh thêm. Bạn vẫn xem được lịch sử điểm danh ở bảng bên ngoài."
      />
    );
  }

  return (
    <>
      <div className="flex justify-end">
        <GlassButton variant="ghost" size="sm" onClick={() => form.markAll(ATTENDANCE_STATUS.present)}>
          Tất cả có mặt
        </GlassButton>
      </div>
      <ul className="flex max-h-[45vh] flex-col gap-2 overflow-y-auto">
        {form.rows.map((row) => (
          <li key={row.enrollmentId} className="flex flex-wrap items-center justify-between gap-2">
            <span className="text-sm text-mist-100">{row.studentName ?? "Học viên"}</span>
            <StatusPicker
              value={form.statuses[row.enrollmentId] ?? ATTENDANCE_STATUS.present}
              onChange={(status) => form.setStatus(row.enrollmentId, status)}
            />
          </li>
        ))}
      </ul>
    </>
  );
}

function StatusPicker({
  value,
  onChange,
}: {
  readonly value: AttendanceStatus;
  readonly onChange: (status: AttendanceStatus) => void;
}) {
  return (
    <div className="flex flex-wrap gap-1" role="radiogroup">
      {ATTENDANCE_STATUS_ORDER.map((status) => (
        <button
          key={status}
          type="button"
          role="radio"
          aria-checked={value === status}
          onClick={() => onChange(status)}
          className={cx(
            "rounded-full border px-3 py-1 text-xs transition",
            value === status
              ? "border-orange-300 bg-orange-100 text-orange-700"
              : "border-slate-200 bg-white/60 text-slate-600 hover:bg-white",
          )}
        >
          {ATTENDANCE_STATUS_LABEL[status]}
        </button>
      ))}
    </div>
  );
}
