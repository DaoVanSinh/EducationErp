import { useClasses } from "@/entities/class";
import { useEnrollments } from "@/entities/enrollment";
import { useStudents } from "@/entities/student";
import {
  useCompleteEnrollment,
  useWithdrawEnrollment,
} from "@/modules/enrollment/api/use-enrollment-mutations";
import { DEFAULT_PAGE_SIZE } from "@/shared/constants/query-config";
import { useCallback, useState } from "react";

/** Số dòng nạp cho hai dropdown tham chiếu (học viên/lớp) - đủ cho một trung tâm, không cần phân trang. */
const REFERENCE_PAGE_SIZE = 200;

/** Toàn bộ state/mutation của trang ghi danh - ui/* chỉ render (Mandate #2). */
export function useEnrollmentsPageController() {
  const [page, setPage] = useState(0);
  const [studentFilter, setStudentFilter] = useState("");
  const [classFilter, setClassFilter] = useState("");
  const [createDialogOpen, setCreateDialogOpen] = useState(false);

  const enrollments = useEnrollments(
    page,
    DEFAULT_PAGE_SIZE,
    studentFilter.length === 0 ? undefined : studentFilter,
    classFilter.length === 0 ? undefined : classFilter,
  );
  const students = useStudents(0, REFERENCE_PAGE_SIZE);
  const classes = useClasses(0, REFERENCE_PAGE_SIZE);
  const withdraw = useWithdrawEnrollment();
  const complete = useCompleteEnrollment();

  const openCreateDialog = useCallback(() => setCreateDialogOpen(true), []);
  const closeCreateDialog = useCallback(() => setCreateDialogOpen(false), []);

  const onWithdraw = useCallback(
    (enrollmentId: string) => {
      void withdraw.mutateAsync(enrollmentId);
    },
    [withdraw],
  );

  const onComplete = useCallback(
    (enrollmentId: string) => {
      void complete.mutateAsync(enrollmentId);
    },
    [complete],
  );

  return {
    page,
    setPage,
    enrollments,
    studentFilter,
    setStudentFilter,
    classFilter,
    setClassFilter,
    students,
    classes,
    createDialogOpen,
    openCreateDialog,
    closeCreateDialog,
    onWithdraw,
    onComplete,
    isMutating: withdraw.isPending || complete.isPending,
  };
}
