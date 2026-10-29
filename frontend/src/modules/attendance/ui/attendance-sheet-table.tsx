import { ATTENDANCE_STATUS_LABEL, type AttendanceSession, type StudentAttendanceRow } from "@/entities/attendance";
import {
  attendancePercentLabel,
  attendancePercentTone,
  attendanceShortLabel,
  attendanceTone,
} from "@/modules/attendance/model/attendance-rules";
import { ENROLLMENT_STATUS_LABEL } from "@/shared/api/schemas";
import { formatter } from "@/shared/lib/format";
import { Badge } from "@/shared/ui/badge";

export interface AttendanceSheetTableProps {
  readonly sessions: readonly AttendanceSession[];
  readonly rows: readonly StudentAttendanceRow[];
}

/** Bảng điểm danh: học viên × buổi (C = có mặt, M = muộn, V = vắng, P = có phép) và tỷ lệ chuyên cần. */
export function AttendanceSheetTable({ sessions, rows }: AttendanceSheetTableProps) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[640px] text-left">
        <thead>
          <tr className="text-xs text-mist-500">
            <th scope="col" className="p-2 font-normal">Học viên</th>
            {sessions.map((session) => (
              <th key={session.sessionId} scope="col" className="p-2 text-center font-normal" title={session.note ?? undefined}>
                {formatter.date(session.sessionDate)}
              </th>
            ))}
            <th scope="col" className="p-2 font-normal">Chuyên cần</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={row.enrollmentId} className="border-t border-white/40">
              <td className="p-2">
                <p className="text-sm text-mist-100">{row.studentName ?? "Học viên"}</p>
                <p className="text-xs text-mist-400">{ENROLLMENT_STATUS_LABEL[row.enrollmentStatus]}</p>
              </td>
              {sessions.map((session) => (
                <td key={session.sessionId} className="p-2 text-center">
                  <StatusCell row={row} sessionId={session.sessionId} />
                </td>
              ))}
              <td className="p-2">
                <Badge tone={attendancePercentTone(row.summary.attendancePercent)}>
                  {attendancePercentLabel(row.summary.attendancePercent)}
                </Badge>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function StatusCell({ row, sessionId }: { readonly row: StudentAttendanceRow; readonly sessionId: string }) {
  const status = row.statusesBySessionId[sessionId];
  if (status === undefined) {
    return <span className="text-xs text-mist-400">—</span>;
  }
  const note = row.notesBySessionId[sessionId];
  return (
    <span title={note ? `${ATTENDANCE_STATUS_LABEL[status]}: ${note}` : ATTENDANCE_STATUS_LABEL[status]}>
      <Badge tone={attendanceTone(status)}>{attendanceShortLabel(status)}</Badge>
    </span>
  );
}
