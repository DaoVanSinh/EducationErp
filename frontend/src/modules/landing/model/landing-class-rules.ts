import { CLASS_STATUS } from "@/entities/class";
import type { LandingClass } from "@/modules/landing/model/landing-schema";
import { formatter } from "@/shared/lib/format";

export interface LandingClassAvailability {
  /** "Đang tuyển sinh · khai giảng 20/11/2026" hoặc "Đã khai giảng 16/10/2026". */
  readonly timeline: string;
  readonly seats: string;
  readonly isFull: boolean;
  readonly reserveLabel: string;
}

/**
 * Khách cần biết ba điều trước khi giữ chỗ: lớp đã học chưa, học từ ngày nào, còn chỗ không. Lớp đang học vẫn
 * nhận học viên (nghiệp vụ ghi danh), nhưng phải nói rõ là vào giữa chừng.
 */
export function landingClassAvailability(cls: LandingClass): LandingClassAvailability {
  const isFull = cls.seatsLeft <= 0;
  const timeline =
    cls.status === CLASS_STATUS.enrolling
      ? `Đang tuyển sinh · khai giảng ${formatter.date(cls.startDate)}`
      : `Đã khai giảng ${formatter.date(cls.startDate)} · vẫn nhận học viên`;
  return {
    timeline,
    seats: isFull ? `Đã đủ ${cls.maxSeats} chỗ` : `Còn ${cls.seatsLeft}/${cls.maxSeats} chỗ`,
    isFull,
    reserveLabel: isFull ? "Đăng ký chờ chỗ" : "Giữ chỗ lớp này",
  };
}

/** Khoá chưa cấu hình số buổi thì nói thật là linh hoạt, không bịa ra một con số. */
export function sessionCountLabel(standardSessionCount: number | null): string {
  return standardSessionCount === null ? "Lộ trình linh hoạt" : `${standardSessionCount} buổi tiêu chuẩn`;
}
