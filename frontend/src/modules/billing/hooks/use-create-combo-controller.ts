import { useComboDiscountTiers, type CreateComboPayload } from "@/entities/billing";
import { useCourses } from "@/entities/course";
import { ENROLLMENT_STATUS, useEnrollments } from "@/entities/enrollment";
import { useStudents } from "@/entities/student";
import { useCreateCombo } from "@/modules/billing/api/use-billing-mutations";
import { createComboFormSchema } from "@/modules/billing/model/billing-forms";
import {
  previewDiscountedTotal,
  resolveDiscountPercent,
} from "@/modules/billing/model/combo-pricing";
import { useZodForm } from "@/shared/lib/use-zod-form";
import { useCallback, useMemo } from "react";

/** Đủ cho một trung tâm; hai dropdown tham chiếu không cần phân trang (mirror các controller khác). */
const REFERENCE_PAGE_SIZE = 200;

/** Một khoá đã chọn, kèm nhãn và học phí để dialog chỉ việc render (Mandate #2). */
export interface ComboCandidate {
  readonly enrollmentId: string;
  readonly label: string;
  readonly tuitionFee: number | null;
}

/**
 * Toàn bộ state/query/mutation của dialog tạo combo. Trình tự: chọn học viên → nạp ghi danh ACTIVE
 * của chính học viên đó (filter server-side, spec mục 7) → tick ≥ 2 khoá → xem trước % giảm và tổng
 * sau giảm → nhập hạn đóng → tạo.
 */
export function useCreateComboController(onCreated: () => void) {
  const createCombo = useCreateCombo();
  const students = useStudents(0, REFERENCE_PAGE_SIZE);
  const courses = useCourses(0, REFERENCE_PAGE_SIZE);
  const tiers = useComboDiscountTiers();

  const form = useZodForm({
    schema: createComboFormSchema,
    initialValues: { studentProfileId: "", enrollmentIds: [], dueDate: "" },
    onSubmit: async (values) => {
      const payload: CreateComboPayload = {
        studentProfileId: values.studentProfileId,
        enrollmentIds: values.enrollmentIds,
        dueDate: values.dueDate,
      };
      await createCombo.mutateAsync(payload);
      form.reset();
      onCreated();
    },
  });

  const selectedStudentId = form.values.studentProfileId;
  // Chỉ nạp khi đã chọn học viên: danh sách ghi danh của MỘT học viên, đã lọc ACTIVE ở server.
  const enrollments = useEnrollments(
    0,
    REFERENCE_PAGE_SIZE,
    selectedStudentId.length === 0 ? undefined : selectedStudentId,
    undefined,
    ENROLLMENT_STATUS.active,
  );

  const tuitionByCourseId = useMemo(() => {
    const map = new Map<string, number | null>();
    (courses.data?.items ?? []).forEach((course) => map.set(course.id, course.tuitionFee));
    return map;
  }, [courses.data]);

  const candidates: readonly ComboCandidate[] = useMemo(
    () =>
      (enrollments.data?.items ?? []).map((enrollment) => ({
        enrollmentId: enrollment.id,
        label: `Lớp ${enrollment.classId.slice(0, 8)} — khoá ${enrollment.courseId.slice(0, 8)}`,
        tuitionFee: tuitionByCourseId.get(enrollment.courseId) ?? null,
      })),
    [enrollments.data, tuitionByCourseId],
  );

  const selectedIds = form.values.enrollmentIds;

  const totalOriginalAmount = useMemo(
    () =>
      candidates
        .filter((candidate) => selectedIds.includes(candidate.enrollmentId))
        .reduce((sum, candidate) => sum + (candidate.tuitionFee ?? 0), 0),
    [candidates, selectedIds],
  );

  const discountPercent = resolveDiscountPercent(tiers.data ?? [], selectedIds.length);
  const discountedTotal =
    discountPercent === null ? null : previewDiscountedTotal(totalOriginalAmount, discountPercent);

  const onSelectStudent = useCallback(
    (studentProfileId: string) => {
      form.setValue("studentProfileId", studentProfileId);
      // Đổi học viên thì các khoá đã tick không còn thuộc về ai - xoá, không giữ lại.
      form.setValue("enrollmentIds", []);
    },
    [form],
  );

  const onToggleEnrollment = useCallback(
    (enrollmentId: string) => {
      const next = selectedIds.includes(enrollmentId)
        ? selectedIds.filter((id) => id !== enrollmentId)
        : [...selectedIds, enrollmentId];
      form.setValue("enrollmentIds", next);
    },
    [form, selectedIds],
  );

  return {
    form,
    students,
    candidates,
    selectedIds,
    isLoadingCandidates: selectedStudentId.length > 0 && enrollments.isPending,
    totalOriginalAmount,
    discountPercent,
    discountedTotal,
    tiersError: tiers.error,
    onSelectStudent,
    onToggleEnrollment,
  };
}
