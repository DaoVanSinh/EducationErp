import { ENROLLMENT_STATUS } from "@/shared/api/schemas";
import { z } from "zod";

/** Bản sao của AttendanceConstants.AttendanceStatus ở backend. */
export const ATTENDANCE_STATUS = {
  present: "PRESENT",
  late: "LATE",
  absent: "ABSENT",
  excused: "EXCUSED",
} as const;

export type AttendanceStatus = (typeof ATTENDANCE_STATUS)[keyof typeof ATTENDANCE_STATUS];

export const ATTENDANCE_STATUS_LABEL: Record<AttendanceStatus, string> = {
  [ATTENDANCE_STATUS.present]: "Có mặt",
  [ATTENDANCE_STATUS.late]: "Đi muộn",
  [ATTENDANCE_STATUS.absent]: "Vắng",
  [ATTENDANCE_STATUS.excused]: "Vắng có phép",
};

/** Thứ tự hiển thị các lựa chọn khi điểm danh. */
export const ATTENDANCE_STATUS_ORDER: readonly AttendanceStatus[] = [
  ATTENDANCE_STATUS.present,
  ATTENDANCE_STATUS.late,
  ATTENDANCE_STATUS.absent,
  ATTENDANCE_STATUS.excused,
];

const attendanceStatusSchema = z.enum([
  ATTENDANCE_STATUS.present,
  ATTENDANCE_STATUS.late,
  ATTENDANCE_STATUS.absent,
  ATTENDANCE_STATUS.excused,
]);

const enrollmentStatusSchema = z.enum([
  ENROLLMENT_STATUS.active,
  ENROLLMENT_STATUS.withdrawn,
  ENROLLMENT_STATUS.completed,
]);

/** `attendancePercent` null = chưa có buổi nào tính được, KHÔNG phải 0%. */
export const attendanceSummarySchema = z.object({
  presentCount: z.number().int(),
  lateCount: z.number().int(),
  absentCount: z.number().int(),
  excusedCount: z.number().int(),
  attendancePercent: z.number().int().nullable(),
});

export type AttendanceSummary = z.infer<typeof attendanceSummarySchema>;

export const attendanceSessionSchema = z.object({
  sessionId: z.string().uuid(),
  sessionDate: z.string(),
  note: z.string().nullable(),
  summary: attendanceSummarySchema,
});

export type AttendanceSession = z.infer<typeof attendanceSessionSchema>;

/** Khuyết khoá sessionId nghĩa là buổi đó chưa ghi nhận học viên này. */
export const studentAttendanceRowSchema = z.object({
  enrollmentId: z.string().uuid(),
  studentProfileId: z.string().uuid(),
  studentName: z.string().nullable(),
  enrollmentStatus: enrollmentStatusSchema,
  statusesBySessionId: z.record(z.string(), attendanceStatusSchema),
  notesBySessionId: z.record(z.string(), z.string()),
  summary: attendanceSummarySchema,
});

export type StudentAttendanceRow = z.infer<typeof studentAttendanceRowSchema>;

export const classAttendanceSchema = z.object({
  classId: z.string().uuid(),
  sessions: z.array(attendanceSessionSchema),
  rows: z.array(studentAttendanceRowSchema),
});

export type ClassAttendance = z.infer<typeof classAttendanceSchema>;

export const myAttendanceClassSchema = z.object({
  enrollmentId: z.string().uuid(),
  classId: z.string().uuid(),
  classCode: z.string().nullable(),
  courseName: z.string().nullable(),
  enrollmentStatus: enrollmentStatusSchema,
  summary: attendanceSummarySchema,
  sessions: z.array(
    z.object({
      sessionDate: z.string(),
      status: attendanceStatusSchema,
      note: z.string().nullable(),
    }),
  ),
});

export type MyAttendanceClass = z.infer<typeof myAttendanceClassSchema>;

export const myAttendanceSchema = z.array(myAttendanceClassSchema);

/** Khớp `attendance.dto.RecordAttendanceRequest`. Upsert theo (classId, sessionDate). */
export interface RecordAttendancePayload {
  readonly classId: string;
  readonly sessionDate: string;
  readonly note: string | null;
  readonly entries: readonly {
    readonly enrollmentId: string;
    readonly status: AttendanceStatus;
    readonly note: string | null;
  }[];
}
