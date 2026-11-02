import { useGroupRegistrations, type GroupRegistration } from "@/entities/billing";
import { useCourses } from "@/entities/course";
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

/** Toàn bộ state/query/mutation của trang Đăng ký nhóm - ui/* chỉ render (Mandate #2). */
export function useGroupRegistrationsPageController() {
  const [page, setPage] = useState(0);
  const [courseFilter, setCourseFilter] = useState("");
  const [createDialogOpen, setCreateDialogOpen] = useState(false);
  const [cancelTarget, setCancelTarget] = useState<GroupRegistrationRow | null>(null);

  const groups = useGroupRegistrations(page, DEFAULT_PAGE_SIZE, courseFilter.length === 0 ? undefined : courseFilter);
  const courses = useCourses(0, REFERENCE_PAGE_SIZE);
  const cancelGroup = useCancelGroupRegistration();

  const rows: readonly GroupRegistrationRow[] = useMemo(
    () =>
      (groups.data?.items ?? []).map((group) => ({
        group,
        courseName: group.courseName ?? "Khoá học",
        memberNames: group.members.map((member) => member.studentName ?? "Học viên"),
      })),
    [groups.data],
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
    courseOptions: courses.data?.items ?? [],
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
