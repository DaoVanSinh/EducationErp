import { classStatusSchema, weeklyScheduleSlotSchema } from "@/entities/class";
import { ENROLLMENT_STATUS } from "@/shared/api/schemas";
import { z } from "zod";

/** Khớp `academic.dto.MyGradeItem`. `score` null = giáo viên chưa chấm cột này, KHÔNG phải 0. */
export const myGradeItemSchema = z.object({
  columnId: z.string().uuid(),
  name: z.string(),
  weightPercent: z.number(),
  maxScore: z.number(),
  score: z.number().nullable(),
});

export type MyGradeItem = z.infer<typeof myGradeItemSchema>;

/**
 * Khớp `academic.dto.MyLearningClassResponse`. `finalScore`/`progressPercent` null là "chưa đủ dữ
 * liệu" (chưa chấm đủ cột / khóa chưa cấu hình số buổi chuẩn) - giao diện không được in 0.
 */
export const myLearningClassSchema = z.object({
  enrollmentId: z.string().uuid(),
  classId: z.string().uuid(),
  classCode: z.string().nullable(),
  courseId: z.string().uuid(),
  courseName: z.string().nullable(),
  teacherName: z.string().nullable(),
  status: z.enum([ENROLLMENT_STATUS.active, ENROLLMENT_STATUS.withdrawn, ENROLLMENT_STATUS.completed]),
  enrolledAt: z.string(),
  schedule: z.array(weeklyScheduleSlotSchema),
  grades: z.array(myGradeItemSchema),
  finalScore: z.number().nullable(),
  elapsedSessions: z.number().int(),
  standardSessionCount: z.number().int().nullable(),
  progressPercent: z.number().int().nullable(),
  /** Trạng thái lớp: đang tuyển sinh thì học viên thấy "khai giảng ngày ..." thay vì một lịch chưa bắt đầu. */
  classStatus: classStatusSchema,
  classStartDate: z.string(),
  classEndDate: z.string().nullable(),
});

export type MyLearningClass = z.infer<typeof myLearningClassSchema>;

export const myLearningSchema = z.array(myLearningClassSchema);
