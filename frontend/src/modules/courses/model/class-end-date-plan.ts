import { estimateEndDate, sessionsPerWeek, type WeeklyScheduleSlot } from "@/entities/class";

export interface EndDatePlan {
  /** Ngày học xong buổi cuối, `null` khi chưa đủ dữ liệu để tính. */
  readonly endDate: string | null;
  /** Dòng gợi ý dưới ô "Ngày kết thúc dự kiến" - nói rõ vì sao tính được hoặc chưa. */
  readonly hint: string;
}

/**
 * Ngày kết thúc dự kiến của lớp, từ số buổi chuẩn của khoá học + ngày khai giảng + lịch tuần. Ô vẫn sửa tay
 * được (nghỉ lễ, học bù); đổi khoá học, ngày khai giảng hoặc lịch thì tính lại.
 */
export function planEndDate(
  startDate: string,
  schedule: readonly WeeklyScheduleSlot[],
  sessionCount: number | null,
): EndDatePlan {
  if (sessionCount === null || sessionCount <= 0) {
    return { endDate: null, hint: "Khoá học chưa có số buổi chuẩn nên không tự tính được. Không bắt buộc." };
  }
  if (schedule.length === 0) {
    return { endDate: null, hint: `Thêm lịch học để tự tính ngày học xong ${sessionCount} buổi.` };
  }
  return {
    endDate: estimateEndDate(startDate, schedule, sessionCount),
    hint: `Tự tính: ${sessionCount} buổi, ${sessionsPerWeek(schedule)} buổi/tuần. Có thể sửa tay nếu có ngày nghỉ.`,
  };
}
