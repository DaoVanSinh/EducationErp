import {
  ATTENDANCE_STATUS,
  type AttendanceStatus,
  type ClassAttendance,
  type StudentAttendanceRow,
} from "@/entities/attendance";
import { useRecordAttendance } from "@/modules/attendance/api/use-attendance-mutations";
import { ENROLLMENT_STATUS } from "@/shared/api/schemas";
import { todayLocalIsoDate } from "@/shared/lib/local-date";
import { useCallback, useMemo, useState, type FormEvent } from "react";

type StatusMap = Readonly<Record<string, AttendanceStatus>>;

/** Học viên đang học - người đã rút/hoàn tất không cần điểm danh nữa. */
function activeRows(attendance: ClassAttendance): readonly StudentAttendanceRow[] {
  return attendance.rows.filter((row) => row.enrollmentStatus === ENROLLMENT_STATUS.active);
}

/**
 * Trạng thái ban đầu của một ngày: nếu ngày đó đã điểm danh thì nạp lại để SỬA, chưa thì mặc định
 * "Có mặt" cho mọi học viên - giáo viên chỉ cần đổi những người vắng.
 */
function initialStatuses(attendance: ClassAttendance, date: string): StatusMap {
  const session = attendance.sessions.find((item) => item.sessionDate === date);
  return Object.fromEntries(
    activeRows(attendance).map((row) => {
      const existing = session ? row.statusesBySessionId[session.sessionId] : undefined;
      return [row.enrollmentId, existing ?? ATTENDANCE_STATUS.present];
    }),
  );
}

/** Toàn bộ state + submit của hộp thoại "Điểm danh buổi" (Mandate #2). */
export function useAttendanceSessionForm(classId: string, attendance: ClassAttendance, onDone: () => void) {
  const recordAttendance = useRecordAttendance(classId);
  const [sessionDate, setSessionDate] = useState(todayLocalIsoDate);
  const [note, setNote] = useState("");
  const [statuses, setStatuses] = useState<StatusMap>(() => initialStatuses(attendance, todayLocalIsoDate()));

  const rows = useMemo(() => activeRows(attendance), [attendance]);

  const changeDate = useCallback(
    (date: string) => {
      setSessionDate(date);
      setStatuses(initialStatuses(attendance, date));
    },
    [attendance],
  );

  const setStatus = useCallback((enrollmentId: string, status: AttendanceStatus) => {
    setStatuses((previous) => ({ ...previous, [enrollmentId]: status }));
  }, []);

  const markAll = useCallback(
    (status: AttendanceStatus) => {
      setStatuses(Object.fromEntries(rows.map((row) => [row.enrollmentId, status])));
    },
    [rows],
  );

  const handleSubmit = useCallback(
    (event: FormEvent<HTMLFormElement>) => {
      event.preventDefault();
      const entries = rows.map((row) => ({
        enrollmentId: row.enrollmentId,
        status: statuses[row.enrollmentId] ?? ATTENDANCE_STATUS.present,
        note: null,
      }));
      recordAttendance.mutate(
        { classId, sessionDate, note: note.trim() === "" ? null : note.trim(), entries },
        { onSuccess: onDone },
      );
    },
    [classId, note, onDone, recordAttendance, rows, sessionDate, statuses],
  );

  return {
    rows,
    sessionDate,
    maxDate: todayLocalIsoDate(),
    note,
    statuses,
    changeDate,
    setNote,
    setStatus,
    markAll,
    handleSubmit,
    isSubmitting: recordAttendance.isPending,
    submitError: recordAttendance.error,
  };
}
