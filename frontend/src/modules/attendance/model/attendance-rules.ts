import { ATTENDANCE_STATUS, type AttendanceStatus } from "@/entities/attendance";

/** Nhánh hiển thị tách khỏi JSX (Mandate #3). */
type Tone = "neutral" | "positive" | "warning" | "danger" | "accent";

const STATUS_TONE: Record<AttendanceStatus, Tone> = {
  [ATTENDANCE_STATUS.present]: "positive",
  [ATTENDANCE_STATUS.late]: "warning",
  [ATTENDANCE_STATUS.absent]: "danger",
  [ATTENDANCE_STATUS.excused]: "neutral",
};

/** Ký hiệu một chữ trong ô bảng điểm danh - bảng nhiều buổi phải gọn. */
const STATUS_SHORT: Record<AttendanceStatus, string> = {
  [ATTENDANCE_STATUS.present]: "C",
  [ATTENDANCE_STATUS.late]: "M",
  [ATTENDANCE_STATUS.absent]: "V",
  [ATTENDANCE_STATUS.excused]: "P",
};

export function attendanceTone(status: AttendanceStatus): Tone {
  return STATUS_TONE[status];
}

export function attendanceShortLabel(status: AttendanceStatus): string {
  return STATUS_SHORT[status];
}

/** Ngưỡng cảnh báo chuyên cần thấp - dưới mức này tô đỏ để giáo viên chú ý. */
const LOW_ATTENDANCE_PERCENT = 80;

export function attendancePercentTone(percent: number | null): Tone {
  if (percent === null) {
    return "neutral";
  }
  return percent < LOW_ATTENDANCE_PERCENT ? "danger" : "positive";
}

export function attendancePercentLabel(percent: number | null): string {
  return percent === null ? "Chưa có buổi" : `${percent}%`;
}
