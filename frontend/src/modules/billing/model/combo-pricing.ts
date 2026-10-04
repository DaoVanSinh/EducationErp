import type { ComboDiscountTier } from "@/entities/billing";

/**
 * Xem trước % giảm theo đúng quy tắc của backend (spec mục 4): bậc có minCourseCount LỚN NHẤT còn
 * active mà không vượt số khoá đã chọn. Trả về null khi chưa cấu hình bậc nào thoả - lúc đó UI phải
 * nói rõ "chưa cấu hình", KHÔNG được hiển thị 0% như thể đó là một mức giảm hợp lệ.
 *
 * Đây chỉ là xem trước. Con số chốt do CreateCombo ở backend tính và snapshot vào Combo; nếu admin
 * vừa đổi bậc thì backend là nguồn sự thật, không phải màn hình này.
 */
export function resolveDiscountPercent(
  tiers: readonly ComboDiscountTier[],
  courseCount: number,
): number | null {
  const applicable = tiers.filter((tier) => tier.active && tier.minCourseCount <= courseCount);
  if (applicable.length === 0) {
    return null;
  }
  return applicable.reduce((best, tier) => (tier.minCourseCount > best.minCourseCount ? tier : best))
    .discountPercent;
}

/** Khớp BillingRules.discountedTotal: nhân rồi làm tròn về đồng nguyên (scale 0). */
export function previewDiscountedTotal(totalOriginalAmount: number, discountPercent: number): number {
  return Math.round(totalOriginalAmount * (1 - discountPercent / 100));
}
