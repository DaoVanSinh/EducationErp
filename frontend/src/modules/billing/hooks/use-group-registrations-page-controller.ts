import { useGroupRegistrations, type GroupRegistration } from "@/entities/billing";
import { useCourses } from "@/entities/course";
import { useStudents } from "@/entities/student";
import { useCancelGroupRegistration } from "@/modules/billing/api/use-billing-mutations";
import { DEFAULT_PAGE_SIZE } from "@/shared/constants/query-config";
import { useCallback, useMemo, useState } from "react";

const REFERENCE_PAGE_SIZE = 200;

/** Một nhóm kèm tên khoá và tên từng người, để bảng chỉ việc render (Mandate #2). */
export interface GroupRegistrationRow {
  readonly group: GroupRegistration;
  readonly courseName: string;
  readonly memberNames: readonly string[];
}

function useNameMaps() {
  const students = useStudents(0, REFERENCE_PAGE_SIZE);
  const courses = useCourses(0, REFERENCE_PAGE_SIZE);
  return useMemo(() => {
    const studentNames = new Map<string, string>();
    (students.data?.items ?? []).forEach((student) =>
      studentNames.set(student.id, student.fullName ?? student.email ?? student.id.slice(0, 8)),
    );
    const courseNames = new Map<string, string>();
    (courses.data?.items ?? []).forEach((course) => courseNames.set(course.id, `${course.code} · ${course.name}`));
    return { studentNames, courseNames, courseOptions: courses.data?.items ?? [] };
  }, [students.data, courses.data]);
}

/** Toàn bộ state/query/mutation của trang Đăng ký nhóm - ui/* chỉ render (Mandate #2). */
export function useGroupRegistrationsPageController() {
  const [page, setPage] = useState(0);
  const [courseFilter, setCourseFilter] = useState("");
  const [createDialogOpen, setCreateDialogOpen] = useState(false);
  const [cancelTarget, setCancelTarget] = useState<GroupRegistrationRow | null>(null);

  const groups = useGroupRegistrations(page, DEFAULT_PAGE_SIZE, courseFilter.length === 0 ? undefined : courseFilter);
  const { studentNames, courseNames, courseOptions } = useNameMaps();
  const cancelGroup = useCancelGroupRegistration();

  const rows: readonly GroupRegistrationRow[] = useMemo(
    () =>
      (groups.data?.items ?? []).map((group) => ({
        group,
        courseName: courseNames.get(group.courseId) ?? group.courseId.slice(0, 8),
        memberNames: group.members.map(
          (member) => studentNames.get(member.studentProfileId) ?? member.studentProfileId.slice(0, 8),
        ),
      })),
    [groups.data, courseNames, studentNames],
  );

  const onSelectCourse = useCallback((courseId: string) => {
    setCourseFilter(courseId);
    setPage(0);
  }, []);

  const closeCancel = useCallback(() => {
    setCancelTarget(null);
    cancelGroup.reset();
  }, [cancelGroup]);

  const confirmCancel = useCallback(() => {
    if (cancelTarget === null) {
      return;
    }
    cancelGroup.mutate(cancelTarget.group.id, { onSuccess: () => setCancelTarget(null) });
  }, [cancelGroup, cancelTarget]);

  return {
    groups,
    rows,
    pageInfo: groups.data,
    setPage,
    courseOptions,
    courseFilter,
    onSelectCourse,
    createDialogOpen,
    openCreateDialog: () => setCreateDialogOpen(true),
    closeCreateDialog: () => setCreateDialogOpen(false),
    cancelTarget,
    askCancel: setCancelTarget,
    closeCancel,
    confirmCancel,
    isCancelling: cancelGroup.isPending,
    cancelError: cancelGroup.error,
  };
}
