import { CLASS_STATUS, type ClassStatus, type ClassSummary } from "@/entities/class/model/class-schema";

/** Lớp còn mở: nhận ghi danh, sửa được (đang tuyển sinh hoặc đang học). */
export function isOpenClass(status: ClassStatus): boolean {
  return status === CLASS_STATUS.enrolling || status === CLASS_STATUS.inProgress;
}

/** Đủ sĩ số tối thiểu để khai giảng chưa. */
export function hasEnoughStudents(cls: Pick<ClassSummary, "activeEnrollments" | "minSeats">): boolean {
  return cls.activeEnrollments >= cls.minSeats;
}

/**
 * Lớp đang tuyển sinh đã tới/qua ngày khai giảng mà vẫn chưa đủ người - tác vụ tự khai giảng sẽ bỏ qua, admin
 * cần dời ngày hoặc huỷ lớp. `today` là "YYYY-MM-DD", so sánh chuỗi ISO là so sánh ngày.
 */
export function isOverdueUnderfilled(
  cls: Pick<ClassSummary, "status" | "startDate" | "activeEnrollments" | "minSeats">,
  today: string,
): boolean {
  return cls.status === CLASS_STATUS.enrolling && cls.startDate <= today && !hasEnoughStudents(cls);
}
