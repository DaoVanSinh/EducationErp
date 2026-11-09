import { INVOICE_STATUS, useInvoices, type InvoiceSummary } from "@/entities/billing";
import { useBranchPaging } from "@/entities/branch";
import { useSuspendStudentAccount } from "@/modules/billing/api/use-billing-mutations";
import { DEFAULT_PAGE_SIZE } from "@/shared/constants/query-config";
import { useCallback, useState } from "react";

/**
 * Danh sách xử lý quá hạn: admin chi nhánh thấy của chi nhánh mình, admin gốc thấy toàn chuỗi. Admin gốc gia hạn, cả hai
 * tạm đóng được tài khoản học viên. Hệ thống không tự rút lớp.
 */
export function useOverdueInvoicesController() {
  const { branchId, page, setPage } = useBranchPaging();
  const invoices = useInvoices(page, DEFAULT_PAGE_SIZE, undefined, undefined, INVOICE_STATUS.overdue, branchId);
  const [extending, setExtending] = useState<InvoiceSummary | null>(null);
  const [suspending, setSuspending] = useState<InvoiceSummary | null>(null);
  const suspendAccount = useSuspendStudentAccount();

  const confirmSuspend = useCallback(() => {
    const accountId = suspending?.studentAccountId;
    if (accountId === null || accountId === undefined) {
      return;
    }
    suspendAccount.mutate(accountId, { onSuccess: () => setSuspending(null) });
  }, [suspendAccount, suspending]);

  return {
    invoices,
    setPage,
    extending,
    openExtend: setExtending,
    closeExtend: () => setExtending(null),
    suspending,
    openSuspend: (invoice: InvoiceSummary) => {
      suspendAccount.reset();
      setSuspending(invoice);
    },
    closeSuspend: () => setSuspending(null),
    confirmSuspend,
    isSuspending: suspendAccount.isPending,
    suspendError: suspendAccount.error,
  };
}
