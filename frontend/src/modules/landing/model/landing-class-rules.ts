import type { LandingClass } from "@/modules/landing/model/landing-schema";
import { formatter } from "@/shared/lib/format";

export interface LandingClassAvailability {
  /** "Đang tuyển sinh · khai giảng 20/11/2026". */
  readonly timeline: string;
  readonly seats: string;
  readonly isFull: boolean;
  readonly reserveLabel: string;
}

/**
 * Khách cần biết hai điều trước khi giữ chỗ: học từ ngày nào, còn chỗ không. Trang này chỉ nhận lớp đang tuyển
 * sinh từ backend, vì lớp đã khai giảng không nhận thêm học viên.
 */
export function landingClassAvailability(cls: LandingClass): LandingClassAvailability {
  const isFull = cls.seatsLeft <= 0;
  return {
    timeline: `Đang tuyển sinh · khai giảng ${formatter.date(cls.startDate)}`,
    seats: isFull ? `Đã đủ ${cls.maxSeats} chỗ` : `Còn ${cls.seatsLeft}/${cls.maxSeats} chỗ`,
    isFull,
    reserveLabel: isFull ? "Đăng ký chờ chỗ" : "Giữ chỗ lớp này",
  };
}

/** Khoá chưa cấu hình số buổi thì nói thật là linh hoạt, không bịa ra một con số. */
export function sessionCountLabel(standardSessionCount: number | null): string {
  return standardSessionCount === null ? "Lộ trình linh hoạt" : `${standardSessionCount} buổi tiêu chuẩn`;
}
