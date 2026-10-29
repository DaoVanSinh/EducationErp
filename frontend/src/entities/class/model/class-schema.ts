import { z } from "zod";

export const DAY_OF_WEEK = {
  mon: "MON",
  tue: "TUE",
  wed: "WED",
  thu: "THU",
  fri: "FRI",
  sat: "SAT",
  sun: "SUN",
} as const;

export type DayOfWeek = (typeof DAY_OF_WEEK)[keyof typeof DAY_OF_WEEK];

export const DAY_OF_WEEK_LABEL: Record<DayOfWeek, string> = {
  [DAY_OF_WEEK.mon]: "Thứ 2",
  [DAY_OF_WEEK.tue]: "Thứ 3",
  [DAY_OF_WEEK.wed]: "Thứ 4",
  [DAY_OF_WEEK.thu]: "Thứ 5",
  [DAY_OF_WEEK.fri]: "Thứ 6",
  [DAY_OF_WEEK.sat]: "Thứ 7",
  [DAY_OF_WEEK.sun]: "Chủ nhật",
};

/**
 * Vòng đời lớp - khớp `CoursesConstants.ClassStatus` ở backend: Đang tuyển sinh → Đang học → Đã kết thúc,
 * hoặc Đang tuyển sinh → Đã huỷ khi không đủ học viên.
 */
export const CLASS_STATUS = {
  enrolling: "ENROLLING",
  inProgress: "IN_PROGRESS",
  completed: "COMPLETED",
  cancelled: "CANCELLED",
} as const;

export type ClassStatus = (typeof CLASS_STATUS)[keyof typeof CLASS_STATUS];

export const CLASS_STATUS_LABEL: Record<ClassStatus, string> = {
  [CLASS_STATUS.enrolling]: "Đang tuyển sinh",
  [CLASS_STATUS.inProgress]: "Đang học",
  [CLASS_STATUS.completed]: "Đã kết thúc",
  [CLASS_STATUS.cancelled]: "Đã huỷ",
};

export const classStatusSchema = z.enum([
  CLASS_STATUS.enrolling,
  CLASS_STATUS.inProgress,
  CLASS_STATUS.completed,
  CLASS_STATUS.cancelled,
]);

export const weeklyScheduleSlotSchema = z.object({
  dayOfWeek: z.enum([
    DAY_OF_WEEK.mon,
    DAY_OF_WEEK.tue,
    DAY_OF_WEEK.wed,
    DAY_OF_WEEK.thu,
    DAY_OF_WEEK.fri,
    DAY_OF_WEEK.sat,
    DAY_OF_WEEK.sun,
  ]),
  startTime: z.string(),
  endTime: z.string(),
});

export type WeeklyScheduleSlot = z.infer<typeof weeklyScheduleSlotSchema>;

export const classSummarySchema = z.object({
  id: z.string().uuid(),
  code: z.string(),
  courseId: z.string().uuid(),
  courseName: z.string(),
  branchId: z.string().uuid(),
  branchName: z.string().nullable(),
  teacherId: z.string().uuid(),
  teacherName: z.string().nullable(),
  maxSeats: z.number().int(),
  minSeats: z.number().int(),
  /** Số học viên đang học - để biết lớp đã đủ sĩ số tối thiểu để khai giảng chưa. */
  activeEnrollments: z.number().int(),
  status: classStatusSchema,
  /** LocalDate "YYYY-MM-DD" - ngày khai giảng (dự kiến khi đang tuyển sinh). */
  startDate: z.string(),
  endDate: z.string().nullable(),
  startedAt: z.string().nullable(),
  endedAt: z.string().nullable(),
  schedule: z.array(weeklyScheduleSlotSchema),
});

export type ClassSummary = z.infer<typeof classSummarySchema>;

export interface CreateClassPayload {
  readonly courseId: string;
  readonly code: string;
  readonly branchId: string;
  readonly teacherId: string;
  readonly maxSeats: number;
  readonly minSeats: number;
  readonly startDate: string;
  readonly endDate: string | null;
  readonly schedule: readonly WeeklyScheduleSlot[];
}

/** Trạng thái lớp không sửa ở đây - lớp chuyển trạng thái qua khai giảng/kết thúc/huỷ. */
export interface UpdateClassPayload {
  readonly teacherId: string;
  readonly maxSeats: number;
  readonly minSeats: number;
  readonly startDate: string;
  readonly endDate: string | null;
  readonly schedule: readonly WeeklyScheduleSlot[];
}
