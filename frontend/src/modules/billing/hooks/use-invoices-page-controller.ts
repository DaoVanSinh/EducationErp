import { useInvoices } from "@/entities/billing";
import { useEnrollments } from "@/entities/enrollment";
import { DEFAULT_PAGE_SIZE } from "@/shared/constants/query-config";
import { useCallback, useState } from "react";

const REFERENCE_PAGE_SIZE = 200;

export function useInvoicesPageController() {
  const [page, setPage] = useState(0);
  const [enrollmentFilter, setEnrollmentFilter] = useState("");
  const [statusFilter, setStatusFilter] = useState("");
  const [createDialogOpen, setCreateDialogOpen] = useState(false);

  const invoices = useInvoices(
    page,
    DEFAULT_PAGE_SIZE,
    enrollmentFilter.length === 0 ? undefined : enrollmentFilter,
    undefined,
    statusFilter.length === 0 ? undefined : statusFilter,
  );
  const enrollments = useEnrollments(0, REFERENCE_PAGE_SIZE);

  const openCreateDialog = useCallback(() => setCreateDialogOpen(true), []);
  const closeCreateDialog = useCallback(() => setCreateDialogOpen(false), []);

  return {
    page,
    setPage,
    invoices,
    enrollmentFilter,
    setEnrollmentFilter,
    statusFilter,
    setStatusFilter,
    enrollments,
    createDialogOpen,
    openCreateDialog,
    closeCreateDialog,
  };
}
