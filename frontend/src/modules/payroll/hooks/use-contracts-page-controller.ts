import { useContracts, type ContractSummary } from "@/entities/payroll";
import { useTerminateContract } from "@/modules/payroll/api/use-contracts-mutations";
import { DEFAULT_PAGE_SIZE } from "@/shared/constants/query-config";
import { useCallback, useState } from "react";

/** Toàn bộ state/mutation của trang Hợp đồng - ui/contracts-page.tsx chỉ render (Mandate #2). */
export function useContractsPageController() {
  const [page, setPage] = useState(0);
  const [createDialogOpen, setCreateDialogOpen] = useState(false);
  const [editing, setEditing] = useState<ContractSummary | null>(null);
  const contracts = useContracts(page, DEFAULT_PAGE_SIZE);
  const terminateContract = useTerminateContract();

  const openCreateDialog = useCallback(() => setCreateDialogOpen(true), []);
  const closeCreateDialog = useCallback(() => setCreateDialogOpen(false), []);
  const stopEditing = useCallback(() => setEditing(null), []);

  const onTerminate = useCallback(
    (contractId: string) => {
      void terminateContract.mutateAsync(contractId);
    },
    [terminateContract],
  );

  return {
    page,
    setPage,
    contracts,
    createDialogOpen,
    openCreateDialog,
    closeCreateDialog,
    editing,
    startEditing: setEditing,
    stopEditing,
    onTerminate,
    isTerminating: terminateContract.isPending,
  };
}
