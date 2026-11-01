import { DAY_OF_WEEK, type DayOfWeek, type WeeklyScheduleSlot } from "@/entities/class/model/class-schema";

/** `Date.getUTCDay()` đếm từ Chủ nhật = 0. */
const DAY_OF_WEEK_BY_JS_DAY: readonly DayOfWeek[] = [
  DAY_OF_WEEK.sun,
  DAY_OF_WEEK.mon,
  DAY_OF_WEEK.tue,
  DAY_OF_WEEK.wed,
  DAY_OF_WEEK.thu,
  DAY_OF_WEEK.fri,
  DAY_OF_WEEK.sat,
];

const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;
/** Chặn vòng lặp: khoá học dài nhất cũng không quá 10 năm. */
const MAX_SEARCH_DAYS = 3660;

/** Số buổi học mỗi tuần - hai khung giờ cùng một thứ là hai buổi. */
export function sessionsPerWeek(schedule: readonly Pick<WeeklyScheduleSlot, "dayOfWeek">[]): number {
  return schedule.length;
}

/**
 * Ngày kết thúc dự kiến = ngày diễn ra buổi thứ `sessionCount`, đếm từ ngày khai giảng (tính cả ngày đó) theo
 * lịch tuần. Không trừ ngày nghỉ lễ - người mở lớp sửa tay nếu cần. Trả `null` khi chưa đủ dữ liệu để tính
 * (khoá học chưa có số buổi chuẩn, lớp chưa có lịch, ngày khai giảng chưa chọn).
 *
 * Tính trên lịch UTC thuần cho "YYYY-MM-DD" để không lệch ngày theo múi giờ máy người dùng.
 */
export function estimateEndDate(
  startDate: string,
  schedule: readonly Pick<WeeklyScheduleSlot, "dayOfWeek">[],
  sessionCount: number | null,
): string | null {
  if (sessionCount === null || sessionCount <= 0 || schedule.length === 0 || !ISO_DATE.test(startDate)) {
    return null;
  }
  const cursor = new Date(
    Date.UTC(Number(startDate.slice(0, 4)), Number(startDate.slice(5, 7)) - 1, Number(startDate.slice(8, 10))),
  );
  let held = 0;
  for (let offset = 0; offset < MAX_SEARCH_DAYS; offset++) {
    const dayOfWeek = DAY_OF_WEEK_BY_JS_DAY[cursor.getUTCDay()];
    held += schedule.filter((slot) => slot.dayOfWeek === dayOfWeek).length;
    if (held >= sessionCount) {
      return cursor.toISOString().slice(0, 10);
    }
    cursor.setUTCDate(cursor.getUTCDate() + 1);
  }
  return null;
}
