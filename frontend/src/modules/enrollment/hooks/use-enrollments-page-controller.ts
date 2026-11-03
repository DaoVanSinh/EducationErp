import { useBranchPaging } from "@/entities/branch";
import { acceptsEnrollment, useClasses } from "@/entities/class";
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
  const { branchId, page, setPage } = useBranchPaging();
  const [studentFilter, setStudentFilter] = useState("");
  const [classFilter, setClassFilter] = useState("");
  const [createDialogOpen, setCreateDialogOpen] = useState(false);

  // Đổi chi nhánh thì lớp đang lọc không còn trong danh sách: giữ lại sẽ ra bảng rỗng với ô chọn trống.
  const [previousBranchId, setPreviousBranchId] = useState(branchId);
  if (branchId !== previousBranchId) {
    setPreviousBranchId(branchId);
    setClassFilter("");
  }

  const enrollments = useEnrollments(
    page,
    DEFAULT_PAGE_SIZE,
    studentFilter.length === 0 ? undefined : studentFilter,
    classFilter.length === 0 ? undefined : classFilter,
    undefined,
    branchId,
  );
  const students = useStudents(0, REFERENCE_PAGE_SIZE);
  // Bộ lọc xem được mọi lớp (kể cả đã kết thúc); ô chọn lớp khi ghi danh chỉ lớp đang tuyển sinh.
  const classes = useClasses(0, REFERENCE_PAGE_SIZE, { branchId });
  const openClasses = useClasses(0, REFERENCE_PAGE_SIZE, { openOnly: true, branchId });
  const enrollableClasses = (openClasses.data?.items ?? []).filter((item) => acceptsEnrollment(item.status));
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
    enrollableClasses,
    createDialogOpen,
    openCreateDialog,
    closeCreateDialog,
    onWithdraw,
    onComplete,
    isMutating: withdraw.isPending || complete.isPending,
  };
}
