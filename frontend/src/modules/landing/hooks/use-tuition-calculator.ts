import type { LandingComboTier, LandingCourse } from "@/modules/landing/model/landing-schema";
import { useMemo, useState } from "react";

export interface TuitionCalculationResult {
  readonly selectedCount: number;
  readonly totalSessions: number;
  readonly baseTuitionVnd: number;
  readonly appliedDiscountPercent: number;
  readonly discountSavedVnd: number;
  readonly netTuitionVnd: number;
  readonly costPerSessionVnd: number;
  readonly nextTierHint: string | null;
}

function resolveApplicableDiscount(
  courseCount: number,
  tiers: readonly LandingComboTier[],
): { appliedPercent: number; nextHint: string | null } {
  let appliedPercent = 0;
  let nextTier: LandingComboTier | null = null;

  for (const tier of tiers) {
    if (courseCount >= tier.minCourseCount && tier.discountPercent >= appliedPercent) {
      appliedPercent = tier.discountPercent;
    } else if (courseCount < tier.minCourseCount && nextTier === null) {
      nextTier = tier;
    }
  }

  const nextHint = nextTier
    ? `Chọn thêm ${nextTier.minCourseCount - courseCount} khoá để nhận mức ưu đãi Combo -${nextTier.discountPercent}%`
    : null;

  return { appliedPercent, nextHint };
}

export function useTuitionCalculator(
  courses: readonly LandingCourse[],
  comboTiers: readonly LandingComboTier[],
) {
  const [customSelectedIds, setCustomSelectedIds] = useState<readonly string[] | null>(null);
  const [studyMode, setStudyMode] = useState<"standard" | "intensive">("standard");

  const defaultSelectedIds = useMemo(
    () => courses.slice(0, Math.min(2, courses.length)).map((c) => c.id),
    [courses],
  );

  const selectedCourseIds = customSelectedIds ?? defaultSelectedIds;

  const toggleCourseSelection = (courseId: string) => {
    const current = selectedCourseIds;
    if (current.includes(courseId)) {
      if (current.length <= 1) return;
      setCustomSelectedIds(current.filter((id) => id !== courseId));
    } else {
      setCustomSelectedIds([...current, courseId]);
    }
  };

  const selectPresetCount = (count: number) => {
    const nextIds = courses.slice(0, Math.min(count, courses.length)).map((c) => c.id);
    setCustomSelectedIds(nextIds);
  };

  const calculation: TuitionCalculationResult = useMemo(() => {
    const chosen = courses.filter((c) => selectedCourseIds.includes(c.id));
    const selectedCount = chosen.length;
    const totalSessions = chosen.reduce((sum, c) => sum + (c.standardSessionCount ?? 24), 0);
    const baseTuitionVnd = chosen.reduce((sum, c) => sum + (c.tuitionFee ?? 0), 0);

    const { appliedPercent, nextHint } = resolveApplicableDiscount(selectedCount, comboTiers);
    const discountSavedVnd = Math.round((baseTuitionVnd * appliedPercent) / 100);
    const netTuitionVnd = Math.max(0, baseTuitionVnd - discountSavedVnd);
    const costPerSessionVnd = totalSessions > 0 ? Math.round(netTuitionVnd / totalSessions) : 0;

    return {
      selectedCount,
      totalSessions,
      baseTuitionVnd,
      appliedDiscountPercent: appliedPercent,
      discountSavedVnd,
      netTuitionVnd,
      costPerSessionVnd,
      nextTierHint: nextHint,
    };
  }, [courses, selectedCourseIds, comboTiers]);

  return {
    selectedCourseIds,
    studyMode,
    calculation,
    onToggleCourse: toggleCourseSelection,
    onSelectPresetCount: selectPresetCount,
    onSetStudyMode: setStudyMode,
  };
}
