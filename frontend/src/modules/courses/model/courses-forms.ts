import { DAY_OF_WEEK } from "@/entities/class";
import {
  REQUIRED_TOTAL_WEIGHT_PERCENT,
  WEIGHT_TOLERANCE,
  type GradeColumnSlot,
} from "@/shared/api/schemas";
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

const gradeColumnFormSchema = z.object({
  name: z.string().min(1, "Nhập tên cột điểm"),
  weightPercent: z
    .string()
    .min(1, "Nhập trọng số")
    .transform((value) => Number(value))
    .refine((value) => value > 0, "Trọng số phải lớn hơn 0"),
  maxScore: z
    .string()
    .min(1, "Nhập thang điểm")
    .transform((value) => Number(value))
    .refine((value) => value > 0, "Thang điểm phải lớn hơn 0"),
});

/**
 * Spec mục 2.3: tổng trọng số bắt buộc = 100% (sai số ±0.01). Danh sách RỖNG hợp lệ và có nghĩa
 * "khóa chưa cấu hình cột điểm" - mọi khóa học tạo trước phân hệ này đang ở trạng thái đó, chặn ở
 * đây là khiến chúng không sửa nổi. Backend chặn lần nữa bằng
 * COURSES_GRADE_COLUMN_WEIGHTS_INVALID; luật ở đây chỉ để người dùng thấy lỗi trước khi gửi.
 */
const gradeColumnsFormSchema = z
  .array(gradeColumnFormSchema)
  .superRefine((columns, ctx) => {
    if (columns.length === 0) {
      return;
    }
    const total = columns.reduce((sum, column) => sum + column.weightPercent, 0);
    if (Math.abs(total - REQUIRED_TOTAL_WEIGHT_PERCENT) > WEIGHT_TOLERANCE) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        message: `Tổng trọng số phải bằng ${REQUIRED_TOTAL_WEIGHT_PERCENT}%, đang là ${total}%`,
      });
    }
  });

/** `displayOrder` là vị trí trong danh sách - người dùng sắp cột bằng cách thêm/xoá theo thứ tự,
 * không phải gõ một con số thứ tự riêng. */
export function toGradeColumnPayload(
  columns: readonly { readonly name: string; readonly weightPercent: number; readonly maxScore: number }[],
): GradeColumnSlot[] {
  return columns.map((column, index) => ({
    name: column.name,
    weightPercent: column.weightPercent,
    maxScore: column.maxScore,
    displayOrder: index,
  }));
}

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
  tuitionFee: z
    .string()
    .trim()
    .transform((value) => (value.length === 0 ? null : Number(value)))
    .refine((value) => value === null || value >= 0, "Học phí không được âm"),
  gradeColumns: gradeColumnsFormSchema,
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
  tuitionFee: z
    .string()
    .trim()
    .transform((value) => (value.length === 0 ? null : Number(value)))
    .refine((value) => value === null || value >= 0, "Học phí không được âm"),
  active: z.boolean(),
  gradeColumns: gradeColumnsFormSchema,
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
