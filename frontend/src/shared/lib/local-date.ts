/**
 * Ngày theo lịch của máy người dùng dạng "YYYY-MM-DD" (giá trị của `<input type="date">` và LocalDate ở
 * backend). Không dùng `toISOString()`: hàm đó đổi sang UTC nên trước 7 giờ sáng ở Việt Nam sẽ ra ngày hôm
 * qua.
 */
export function todayLocalIsoDate(): string {
  const now = new Date();
  const month = String(now.getMonth() + 1).padStart(2, "0");
  const day = String(now.getDate()).padStart(2, "0");
  return `${now.getFullYear()}-${month}-${day}`;
}
