import type { LandingComboTier, LandingCourse } from "@/modules/landing/model/landing-schema";
import {
  CALCULATOR_MODE,
  calculateGroup,
  calculateMultiCourse,
  clampGroupSize,
  consultationNote,
  GROUP_SIZE_LIMITS,
  pricedCourses,
  type CalculatorMode,
} from "@/modules/landing/model/tuition-calculator-rules";
import { useMemo, useState } from "react";

export type { TuitionCalculationResult } from "@/modules/landing/model/tuition-calculator-rules";

/** Gói khách đang xem, để nút "Nhận báo giá" điền sẵn form tư vấn. */
export interface CalculatorConsultRequest {
  readonly courseId?: string;
  readonly notes: string;
}

export function useTuitionCalculator(courses: readonly LandingCourse[], comboTiers: readonly LandingComboTier[]) {
  const priced = useMemo(() => pricedCourses(courses), [courses]);
  const [mode, setMode] = useState<CalculatorMode>(CALCULATOR_MODE.multiCourse);
  const [customSelectedIds, setCustomSelectedIds] = useState<readonly string[] | null>(null);
  const [groupCourseId, setGroupCourseId] = useState<string | null>(null);
  const [groupSize, setGroupSize] = useState<number>(GROUP_SIZE_LIMITS.min);

  const defaultSelectedIds = useMemo(() => priced.slice(0, Math.min(2, priced.length)).map((c) => c.id), [priced]);
  const selectedCourseIds = customSelectedIds ?? defaultSelectedIds;
  const selectedGroupCourseId = groupCourseId ?? priced[0]?.id ?? null;

  const chosen = useMemo(() => {
    if (mode === CALCULATOR_MODE.group) {
      return priced.filter((course) => course.id === selectedGroupCourseId);
    }
    return priced.filter((course) => selectedCourseIds.includes(course.id));
  }, [mode, priced, selectedCourseIds, selectedGroupCourseId]);

  const calculation = useMemo(
    () =>
      mode === CALCULATOR_MODE.group
        ? calculateGroup(chosen[0], groupSize, comboTiers)
        : calculateMultiCourse(chosen, comboTiers),
    [mode, chosen, groupSize, comboTiers],
  );

  const onToggleCourse = (courseId: string) => {
    if (!selectedCourseIds.includes(courseId)) {
      setCustomSelectedIds([...selectedCourseIds, courseId]);
      return;
    }
    if (selectedCourseIds.length > 1) {
      setCustomSelectedIds(selectedCourseIds.filter((id) => id !== courseId));
    }
  };

  const onSelectPresetCount = (count: number) =>
    setCustomSelectedIds(priced.slice(0, Math.min(count, priced.length)).map((c) => c.id));

  const consultRequest: CalculatorConsultRequest = {
    courseId: chosen.length === 1 ? chosen[0]?.id : undefined,
    notes: consultationNote(calculation, chosen),
  };

  return {
    mode,
    pricedCourses: priced,
    selectedCourseIds,
    selectedGroupCourseId,
    groupSize,
    calculation,
    consultRequest,
    onSetMode: setMode,
    onToggleCourse,
    onSelectPresetCount,
    onSelectGroupCourse: setGroupCourseId,
    onChangeGroupSize: (people: number) => setGroupSize(clampGroupSize(people)),
  };
}

export type TuitionCalculator = ReturnType<typeof useTuitionCalculator>;
