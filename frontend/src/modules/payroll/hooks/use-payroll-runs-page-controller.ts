import { usePayrollRuns } from "@/entities/payroll";
import { useCreatePayrollRun } from "@/modules/payroll/api/use-payroll-run-mutations";
import { DEFAULT_PAGE_SIZE } from "@/shared/constants/query-config";
import { useCallback, useState } from "react";

export function usePayrollRunsPageController() {
  const [page, setPage] = useState(0);
  const [createDialogOpen, setCreateDialogOpen] = useState(false);
  const runs = usePayrollRuns(page, DEFAULT_PAGE_SIZE);
  const createPayrollRun = useCreatePayrollRun();

  const openCreateDialog = useCallback(() => setCreateDialogOpen(true), []);
  const closeCreateDialog = useCallback(() => setCreateDialogOpen(false), []);

  return { page, setPage, runs, createDialogOpen, openCreateDialog, closeCreateDialog, createPayrollRun };
}
