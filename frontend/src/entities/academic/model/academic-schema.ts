import { ENROLLMENT_STATUS, gradeColumnSchema } from "@/shared/api/schemas";
import { z } from "zod";

const enrollmentStatusSchema = z.enum([
  ENROLLMENT_STATUS.active,
  ENROLLMENT_STATUS.withdrawn,
  ENROLLMENT_STATUS.completed,
]);

/**
 * Một dòng bảng điểm. `scoresByColumnId` là map có khoá là id cột điểm (JSON luôn trả khoá dạng
 * chuỗi). Cột khuyết khoá nghĩa là CHƯA có điểm, và khi đó `finalScore` là null = "chưa đủ điểm"
 * (backend GradeRules) — KHÔNG phải 0, giao diện không được hiển thị 0 ở đây.
 */
export const studentGradesRowSchema = z.object({
  enrollmentId: z.string().uuid(),
  studentProfileId: z.string().uuid(),
  studentName: z.string().nullable(),
  status: enrollmentStatusSchema,
  scoresByColumnId: z.record(z.string(), z.number()),
  finalScore: z.number().nullable(),
});

export type StudentGradesRow = z.infer<typeof studentGradesRowSchema>;

export const gradebookSchema = z.object({
  classId: z.string().uuid(),
  courseId: z.string().uuid(),
  /** Thứ tự cột do backend quyết định theo displayOrder - không sắp lại ở frontend. */
  columns: z.array(gradeColumnSchema),
  rows: z.array(studentGradesRowSchema),
});

export type Gradebook = z.infer<typeof gradebookSchema>;

/**
 * Một dòng bảng tiến trình. `progressPercent` null = khóa chưa cấu hình tổng số buổi chuẩn, không
 * phải 0% — `elapsedSessions` vẫn là số thật để người xem phân biệt hai tình huống.
 */
export const studentProgressRowSchema = z.object({
  enrollmentId: z.string().uuid(),
  studentProfileId: z.string().uuid(),
  studentName: z.string().nullable(),
  status: enrollmentStatusSchema,
  enrolledAt: z.string(),
  withdrawnAt: z.string().nullable(),
  elapsedSessions: z.number().int(),
  standardSessionCount: z.number().int().nullable(),
  progressPercent: z.number().int().nullable(),
});

export type StudentProgressRow = z.infer<typeof studentProgressRowSchema>;

export const classProgressSchema = z.object({
  classId: z.string().uuid(),
  courseId: z.string().uuid(),
  standardSessionCount: z.number().int().nullable(),
  rows: z.array(studentProgressRowSchema),
});

export type ClassProgress = z.infer<typeof classProgressSchema>;

/** Khớp `academic.dto.RecordGradeRequest`. Upsert: gửi lại cùng cặp (enrollmentId, columnId) là SỬA. */
export interface RecordGradePayload {
  readonly classId: string;
  readonly enrollmentId: string;
  readonly courseGradeColumnId: string;
  readonly score: number;
}

/**
 * Một lớp trong tab "Kết quả học tập" của trang chi tiết học viên. `classCode`/`courseName` null
 * nghĩa là lớp đã bị xoá hẳn - hiển thị "(đã xoá)", không bao giờ in "null". `finalScore`/
 * `progressPercent` null là "chưa đủ dữ liệu", không phải 0 - cùng triết lý với
 * `studentGradesRowSchema`/`studentProgressRowSchema`.
 */
export const studentClassSummaryRowSchema = z.object({
  classId: z.string().uuid(),
  classCode: z.string().nullable(),
  courseName: z.string().nullable(),
  status: enrollmentStatusSchema,
  finalScore: z.number().nullable(),
  progressPercent: z.number().int().nullable(),
});

export type StudentClassSummaryRow = z.infer<typeof studentClassSummaryRowSchema>;

/** Danh sách rỗng nghĩa là học viên chưa ghi danh lớp nào, HOẶC người xem (giáo viên) không được
 * phép thấy lớp nào của học viên này - hai tình huống khác nhau nhưng cùng một hình dạng phản hồi. */
export const studentAcademicSummarySchema = z.object({
  studentProfileId: z.string().uuid(),
  classes: z.array(studentClassSummaryRowSchema),
});

export type StudentAcademicSummary = z.infer<typeof studentAcademicSummarySchema>;
