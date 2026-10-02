import { DAY_OF_WEEK } from "@/entities/class";
import { z } from "zod";

const COURSE_CODE_PATTERN = /^[A-Z][A-Z0-9-]*$/;
const CLASS_CODE_PATTERN = /^[A-Z][A-Z0-9-]*$/;

const scheduleSlotFormSchema = z.object({
  dayOfWeek: z.enum([
    DAY_OF_WEEK.mon,
    DAY_OF_WEEK.tue,
    DAY_OF_WEEK.wed,
    DAY_OF_WEEK.thu,
    DAY_OF_WEEK.fri,
    DAY_OF_WEEK.sat,
    DAY_OF_WEEK.sun,
  ]),
  startTime: z.string().min(1, "Chọn giờ bắt đầu"),
  endTime: z.string().min(1, "Chọn giờ kết thúc"),
});

export const createCourseFormSchema = z.object({
  code: z
    .string()
    .min(1, "Nhập mã khóa học")
    .regex(COURSE_CODE_PATTERN, "Mã chỉ gồm chữ in hoa, số và dấu gạch ngang, ví dụ TA-GT"),
  name: z.string().min(1, "Nhập tên khóa học"),
  description: z
    .string()
    .trim()
    .transform((value) => (value.length === 0 ? null : value)),
  standardSessionCount: z
    .string()
    .trim()
    .transform((value) => (value.length === 0 ? null : Number(value))),
});

export const updateCourseFormSchema = z.object({
  name: z.string().min(1, "Nhập tên khóa học"),
  description: z
    .string()
    .trim()
    .transform((value) => (value.length === 0 ? null : value)),
  standardSessionCount: z
    .string()
    .trim()
    .transform((value) => (value.length === 0 ? null : Number(value))),
  active: z.boolean(),
});

export const createClassFormSchema = z.object({
  courseId: z.string().uuid("Chọn khóa học"),
  code: z
    .string()
    .min(1, "Nhập mã lớp")
    .regex(CLASS_CODE_PATTERN, "Mã chỉ gồm chữ in hoa, số và dấu gạch ngang, ví dụ TA-GT-K15"),
  branchId: z.string().uuid("Chọn chi nhánh"),
  teacherId: z.string().uuid("Chọn giáo viên"),
  maxSeats: z
    .string()
    .min(1, "Nhập sĩ số tối đa")
    .transform((value) => Number(value))
    .refine((value) => value > 0, "Sĩ số phải lớn hơn 0"),
  schedule: z.array(scheduleSlotFormSchema),
});

export const updateClassFormSchema = z.object({
  teacherId: z.string().uuid("Chọn giáo viên"),
  maxSeats: z
    .string()
    .min(1, "Nhập sĩ số tối đa")
    .transform((value) => Number(value))
    .refine((value) => value > 0, "Sĩ số phải lớn hơn 0"),
  active: z.boolean(),
  schedule: z.array(scheduleSlotFormSchema),
});
