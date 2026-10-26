import { DAY_OF_WEEK_LABEL, type WeeklyScheduleSlot } from "@/entities/class/model/class-schema";

/** Backend trả LocalTime dạng "HH:mm:ss" - lịch học chỉ cần tới phút. */
function toHourMinute(time: string): string {
  return time.slice(0, 5);
}

/** "Thứ 2 · 18:00–20:00" - một cách hiển thị lịch học dùng chung cho cổng giảng dạy và học tập. */
export function formatScheduleSlot(slot: WeeklyScheduleSlot): string {
  return `${DAY_OF_WEEK_LABEL[slot.dayOfWeek]} · ${toHourMinute(slot.startTime)}–${toHourMinute(slot.endTime)}`;
}
