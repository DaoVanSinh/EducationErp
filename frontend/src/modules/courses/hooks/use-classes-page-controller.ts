import { useClasses, type ClassSummary } from "@/entities/class";
import {
  CLASS_TRANSITION,
  useClassTransition,
  type ClassTransition,
} from "@/modules/courses/api/use-courses-mutations";
import { DEFAULT_PAGE_SIZE } from "@/shared/constants/query-config";
import { todayLocalIsoDate } from "@/shared/lib/local-date";
import { useCallback, useState } from "react";

export interface PendingTransition {
  readonly cls: ClassSummary;
  readonly transition: ClassTransition;
}

/** Nội dung hộp thoại xác nhận cho từng thao tác chuyển trạng thái. */
export const TRANSITION_COPY: Record<
  ClassTransition,
  { readonly title: string; readonly confirmLabel: string; readonly tone: "primary" | "danger" }
> = {
  [CLASS_TRANSITION.start]: { title: "Khai giảng lớp", confirmLabel: "Khai giảng", tone: "primary" },
  [CLASS_TRANSITION.complete]: { title: "Kết thúc lớp", confirmLabel: "Kết thúc lớp", tone: "primary" },
  [CLASS_TRANSITION.cancel]: { title: "Huỷ lớp", confirmLabel: "Huỷ lớp", tone: "danger" },
};

/** Toàn bộ state của trang Lớp học: phân trang, hộp thoại tạo/sửa, và xác nhận chuyển trạng thái lớp. */
export function useClassesPageController() {
  const [page, setPage] = useState(0);
  const [createDialogOpen, setCreateDialogOpen] = useState(false);
  const [editing, setEditing] = useState<ClassSummary | null>(null);
  const [pending, setPending] = useState<PendingTransition | null>(null);
  const classes = useClasses(page, DEFAULT_PAGE_SIZE);
  const transition = useClassTransition();

  const requestTransition = useCallback(
    (cls: ClassSummary, kind: ClassTransition) => {
      transition.reset();
      setPending({ cls, transition: kind });
    },
    [transition],
  );

  const closeTransition = useCallback(() => setPending(null), []);

  const confirmTransition = useCallback(() => {
    if (pending === null) {
      return;
    }
    transition.mutate(
      { classId: pending.cls.id, transition: pending.transition },
      { onSuccess: () => setPending(null) },
    );
  }, [pending, transition]);

  return {
    page,
    setPage,
    classes,
    today: todayLocalIsoDate(),
    createDialogOpen,
    openCreateDialog: () => setCreateDialogOpen(true),
    closeCreateDialog: () => setCreateDialogOpen(false),
    editing,
    startEditing: setEditing,
    stopEditing: () => setEditing(null),
    pending,
    requestTransition,
    closeTransition,
    confirmTransition,
    transitionError: transition.error,
    isTransitioning: transition.isPending,
  };
}
