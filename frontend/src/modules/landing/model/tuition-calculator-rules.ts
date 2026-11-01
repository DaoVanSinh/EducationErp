import type { LandingComboTier, LandingCourse } from "@/modules/landing/model/landing-schema";

/**
 * Hai cách được giảm theo cùng một bảng bậc ("từ N suất học"):
 * - nhiều khoá: một người đăng ký N khoá;
 * - nhóm: N người rủ nhau học chung một khoá, mỗi người tự đóng phần của mình.
 */
export const CALCULATOR_MODE = {
  multiCourse: "multiCourse",
  group: "group",
} as const;

export type CalculatorMode = (typeof CALCULATOR_MODE)[keyof typeof CALCULATOR_MODE];

/** Nhóm từ 2 người (khớp BillingConstants.Limits.MIN_MEMBERS_PER_GROUP); trần 10 cho ô chọn số người. */
export const GROUP_SIZE_LIMITS = { min: 2, max: 10 } as const;

export interface TuitionCalculationResult {
  readonly mode: CalculatorMode;
  /** Số suất dùng để tra bậc: số khoá (nhiều khoá) hoặc số người (nhóm). */
  readonly unitCount: number;
  readonly totalSessions: number;
  readonly baseTuitionVnd: number;
  readonly appliedDiscountPercent: number;
  readonly discountSavedVnd: number;
  readonly netTuitionVnd: number;
  /** Nhóm: mỗi người đóng bao nhiêu. Nhiều khoá: bằng tổng (một người đóng cả). */
  readonly perPersonVnd: number;
  readonly costPerSessionVnd: number;
  readonly nextTierHint: string | null;
}

/** Khoá chưa có giá thì không đưa vào dự toán - tính nó bằng 0 đồng là báo giá sai. */
export function pricedCourses(courses: readonly LandingCourse[]): readonly LandingCourse[] {
  return courses.filter((course) => course.tuitionFee !== null);
}

/** Bậc có mốc cao nhất không vượt số suất - cùng quy tắc với backend (CreateCombo/CreateGroupRegistration). */
export function appliedDiscountPercent(unitCount: number, tiers: readonly LandingComboTier[]): number {
  return tiers
    .filter((tier) => tier.minCourseCount <= unitCount)
    .reduce((best, tier) => (tier.minCourseCount > best.minCourseCount ? tier : best), {
      minCourseCount: 0,
      discountPercent: 0,
    }).discountPercent;
}

function nextTier(unitCount: number, tiers: readonly LandingComboTier[]): LandingComboTier | null {
  const higher = tiers.filter((tier) => tier.minCourseCount > unitCount);
  if (higher.length === 0) {
    return null;
  }
  return higher.reduce((lowest, tier) => (tier.minCourseCount < lowest.minCourseCount ? tier : lowest));
}

function nextTierHint(mode: CalculatorMode, unitCount: number, tiers: readonly LandingComboTier[]): string | null {
  const tier = nextTier(unitCount, tiers);
  if (tier === null) {
    return null;
  }
  const missing = tier.minCourseCount - unitCount;
  if (mode === CALCULATOR_MODE.group) {
    return `Rủ thêm ${missing} người học chung để mỗi người được giảm ${tier.discountPercent}%`;
  }
  return `Chọn thêm ${missing} khoá để nhận mức ưu đãi Combo -${tier.discountPercent}%`;
}

/** Số buổi chưa cấu hình thì không cộng vào - chi phí mỗi buổi chỉ tính trên những buổi biết chắc. */
function knownSessions(courses: readonly LandingCourse[]): number {
  return courses.reduce((sum, course) => sum + (course.standardSessionCount ?? 0), 0);
}

function perSession(amount: number, sessions: number): number {
  return sessions > 0 ? Math.round(amount / sessions) : 0;
}

export function calculateMultiCourse(
  chosen: readonly LandingCourse[],
  tiers: readonly LandingComboTier[],
): TuitionCalculationResult {
  const unitCount = chosen.length;
  const totalSessions = knownSessions(chosen);
  const baseTuitionVnd = chosen.reduce((sum, course) => sum + (course.tuitionFee ?? 0), 0);
  const percent = appliedDiscountPercent(unitCount, tiers);
  const discountSavedVnd = Math.round((baseTuitionVnd * percent) / 100);
  const netTuitionVnd = Math.max(0, baseTuitionVnd - discountSavedVnd);
  return {
    mode: CALCULATOR_MODE.multiCourse,
    unitCount,
    totalSessions,
    baseTuitionVnd,
    appliedDiscountPercent: percent,
    discountSavedVnd,
    netTuitionVnd,
    perPersonVnd: netTuitionVnd,
    costPerSessionVnd: perSession(netTuitionVnd, totalSessions),
    nextTierHint: nextTierHint(CALCULATOR_MODE.multiCourse, unitCount, tiers),
  };
}

export function calculateGroup(
  course: LandingCourse | undefined,
  people: number,
  tiers: readonly LandingComboTier[],
): TuitionCalculationResult {
  const fee = course?.tuitionFee ?? 0;
  const sessions = course?.standardSessionCount ?? 0;
  const percent = appliedDiscountPercent(people, tiers);
  const perPersonVnd = Math.max(0, fee - Math.round((fee * percent) / 100));
  const baseTuitionVnd = fee * people;
  const netTuitionVnd = perPersonVnd * people;
  return {
    mode: CALCULATOR_MODE.group,
    unitCount: people,
    totalSessions: sessions,
    baseTuitionVnd,
    appliedDiscountPercent: percent,
    discountSavedVnd: baseTuitionVnd - netTuitionVnd,
    netTuitionVnd,
    perPersonVnd,
    costPerSessionVnd: perSession(perPersonVnd, sessions),
    nextTierHint: nextTierHint(CALCULATOR_MODE.group, people, tiers),
  };
}

export function clampGroupSize(people: number): number {
  return Math.min(GROUP_SIZE_LIMITS.max, Math.max(GROUP_SIZE_LIMITS.min, people));
}

/** Ghi chú điền sẵn vào form tư vấn, để tư vấn viên biết khách đang hỏi gói nào. */
export function consultationNote(
  calculation: TuitionCalculationResult,
  chosen: readonly LandingCourse[],
): string {
  const names = chosen.map((course) => course.name).join(", ");
  if (calculation.mode === CALCULATOR_MODE.group) {
    return `Đăng ký nhóm ${calculation.unitCount} người học chung khoá ${names} (ưu đãi -${calculation.appliedDiscountPercent}%/người).`;
  }
  return `Đăng ký ${calculation.unitCount} khoá: ${names} (ưu đãi combo -${calculation.appliedDiscountPercent}%).`;
}
