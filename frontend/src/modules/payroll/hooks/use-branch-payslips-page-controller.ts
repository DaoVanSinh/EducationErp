import { useBranchPaging } from "@/entities/branch";
import { useBranchPayslips, type BranchPayslip } from "@/entities/payroll";
import { DEFAULT_PAGE_SIZE } from "@/shared/constants/query-config";
import { useCallback, useState } from "react";

/** Giá trị ô lọc tình trạng: rỗng là tất cả. */
export const PAID_FILTER = { all: "", unpaid: "UNPAID", paid: "PAID" } as const;

type PaidFilter = (typeof PAID_FILTER)[keyof typeof PAID_FILTER];

function toPaidParam(filter: PaidFilter): boolean | undefined {
  if (filter === PAID_FILTER.all) {
    return undefined;
  }
  return filter === PAID_FILTER.paid;
}

/** `period` là giá trị của `<input type="month">`, dạng "YYYY-MM", rỗng là mọi tháng. */
function toYearMonth(period: string): { year?: number; month?: number } {
  const [year, month] = period.split("-").map(Number);
  return year && month ? { year, month } : {};
}

/** Toàn bộ state màn Chi trả lương - ui/* chỉ render (Mandate #2). Mặc định xem phiếu chờ thanh toán, việc cần làm. */
export function useBranchPayslipsPageController() {
  const { branchId, page, setPage } = useBranchPaging();
  const [period, setPeriodValue] = useState("");
  const [paidFilter, setPaidValue] = useState<PaidFilter>(PAID_FILTER.unpaid);
  const [paying, setPaying] = useState<BranchPayslip | null>(null);
  const [undoing, setUndoing] = useState<BranchPayslip | null>(null);
  const payslips = useBranchPayslips(page, DEFAULT_PAGE_SIZE, {
    branchId,
    paid: toPaidParam(paidFilter),
    ...toYearMonth(period),
  });

  const setPeriod = useCallback(
    (value: string) => {
      setPeriodValue(value);
      setPage(0);
    },
    [setPage],
  );
  const setPaidFilter = useCallback(
    (value: PaidFilter) => {
      setPaidValue(value);
      setPage(0);
    },
    [setPage],
  );

  return {
    payslips,
    setPage,
    period,
    setPeriod,
    paidFilter,
    setPaidFilter,
    paying,
    openPay: setPaying,
    closePay: () => setPaying(null),
    undoing,
    openUndo: setUndoing,
    closeUndo: () => setUndoing(null),
  };
}
