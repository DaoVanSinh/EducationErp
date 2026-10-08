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
  active: z.boolean(),
  schedule: z.array(weeklyScheduleSlotSchema),
});

export type ClassSummary = z.infer<typeof classSummarySchema>;

export interface CreateClassPayload {
  readonly courseId: string;
  readonly code: string;
  readonly branchId: string;
  readonly teacherId: string;
  readonly maxSeats: number;
  readonly schedule: readonly WeeklyScheduleSlot[];
}

export interface UpdateClassPayload {
  readonly teacherId: string;
  readonly maxSeats: number;
  readonly active: boolean;
  readonly schedule: readonly WeeklyScheduleSlot[];
}
