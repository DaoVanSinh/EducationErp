import { useComboDiscountTiers } from "@/entities/billing";
import { useClasses } from "@/entities/class";
import { useCourses } from "@/entities/course";
import { useEnrollments } from "@/entities/enrollment";
import { useStudents } from "@/entities/student";
import { useCreateGroupRegistration } from "@/modules/billing/api/use-billing-mutations";
import { createGroupRegistrationFormSchema } from "@/modules/billing/model/billing-forms";
import { previewDiscountedTotal, resolveDiscountPercent } from "@/modules/billing/model/combo-pricing";
import { ENROLLMENT_STATUS } from "@/shared/api/schemas";
import { useZodForm } from "@/shared/lib/use-zod-form";
import { useCallback, useMemo } from "react";

/** Đủ cho một trung tâm; các dropdown tham chiếu không cần phân trang (mirror use-create-combo-controller). */
const REFERENCE_PAGE_SIZE = 200;

/** Một học viên đang học lớp đã chọn, kèm tên để dialog chỉ việc render (Mandate #2). */
export interface GroupCandidate {
  readonly enrollmentId: string;
  readonly label: string;
}

function useStudentNames() {
  const students = useStudents(0, REFERENCE_PAGE_SIZE);
  return useMemo(() => {
    const map = new Map<string, string>();
    (students.data?.items ?? []).forEach((student) =>
      map.set(student.id, student.fullName ?? student.email ?? student.id.slice(0, 8)),
    );
    return map;
  }, [students.data]);
}

/**
 * Toàn bộ state/query/mutation của dialog lập nhóm. Trình tự: chọn lớp → nạp ghi danh đang học của lớp →
 * tick ≥ 2 học viên → xem trước % giảm và học phí mỗi người → tạo. Con số chốt do backend tính.
 */
export function useCreateGroupRegistrationController(onCreated: () => void) {
  const createGroup = useCreateGroupRegistration();
  const classes = useClasses(0, REFERENCE_PAGE_SIZE, { openOnly: true });
  const courses = useCourses(0, REFERENCE_PAGE_SIZE);
  const tiers = useComboDiscountTiers();
  const studentNames = useStudentNames();

  const form = useZodForm({
    schema: createGroupRegistrationFormSchema,
    initialValues: { classId: "", enrollmentIds: [] },
    onSubmit: async (values) => {
      await createGroup.mutateAsync({ enrollmentIds: values.enrollmentIds });
      form.reset();
      onCreated();
    },
  });

  const classId = form.values.classId;
  const enrollments = useEnrollments(
    0,
    REFERENCE_PAGE_SIZE,
    undefined,
    classId.length === 0 ? undefined : classId,
    ENROLLMENT_STATUS.active,
  );

  const selectedClass = (classes.data?.items ?? []).find((cls) => cls.id === classId);
  const tuitionFee =
    (courses.data?.items ?? []).find((course) => course.id === selectedClass?.courseId)?.tuitionFee ?? null;

  const candidates: readonly GroupCandidate[] = useMemo(
    () =>
      (enrollments.data?.items ?? []).map((enrollment) => ({
        enrollmentId: enrollment.id,
        label: studentNames.get(enrollment.studentProfileId) ?? enrollment.studentProfileId.slice(0, 8),
      })),
    [enrollments.data, studentNames],
  );

  const selectedIds = form.values.enrollmentIds;
  const discountPercent = resolveDiscountPercent(tiers.data ?? [], selectedIds.length);
  const perPersonFee =
    discountPercent === null || tuitionFee === null ? null : previewDiscountedTotal(tuitionFee, discountPercent);

  const onSelectClass = useCallback(
    (nextClassId: string) => {
      form.setValue("classId", nextClassId);
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
    classOptions: classes.data?.items ?? [],
    candidates,
    selectedIds,
    isLoadingCandidates: classId.length > 0 && enrollments.isPending,
    tuitionFee,
    discountPercent,
    perPersonFee,
    onSelectClass,
    onToggleEnrollment,
  };
}
