import type { EnrollmentSummary } from "@/entities/enrollment/model/enrollment-schema";

/** "Tên khoá · Lớp mã-lớp" - dùng khi đã biết học viên, ví dụ danh sách chọn khoá của một học viên. */
export function enrollmentClassLabel(enrollment: EnrollmentSummary): string {
  return `${enrollment.courseName ?? "Khoá học"} · Lớp ${enrollment.classCode ?? "—"}`;
}

/** "Tên học viên · Tên khoá · Lớp mã-lớp" - dùng khi danh sách trộn nhiều học viên. */
export function enrollmentFullLabel(enrollment: EnrollmentSummary): string {
  return `${enrollment.studentName ?? "Học viên"} · ${enrollmentClassLabel(enrollment)}`;
}
