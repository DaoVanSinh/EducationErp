import { usePayMyInvoice } from "@/modules/learning/api/use-pay-invoice-mutation";
import { useMyInvoices, useMyLearningClasses } from "@/modules/learning/api/use-learning-queries";
import { tuitionTotals } from "@/modules/learning/model/learning-rules";
import { useCallback, useMemo } from "react";

/**
 * Toàn bộ dữ liệu trang "Học phí của tôi" - tổng tiền và tên khóa học ghép ở đây, ui/* chỉ render.
 * Hoá đơn chỉ mang courseId, nên tên khóa lấy từ chính danh sách lớp đã ghi danh của học viên.
 */
export function useMyInvoicesPageController() {
  const invoicesQuery = useMyInvoices();
  const classesQuery = useMyLearningClasses();

  const invoices = useMemo(() => invoicesQuery.data ?? [], [invoicesQuery.data]);
  const totals = useMemo(() => tuitionTotals(invoices), [invoices]);
  const courseNames = useMemo(() => {
    const names = new Map<string, string>();
    for (const item of classesQuery.data ?? []) {
      if (item.courseName !== null) {
        names.set(item.courseId, item.courseName);
      }
    }
    return names;
  }, [classesQuery.data]);

  const courseNameOf = (courseId: string | null): string | null =>
    courseId === null ? null : (courseNames.get(courseId) ?? null);

  const payMyInvoice = usePayMyInvoice();
  /** Tạo link VNPay rồi rời trang sang cổng; quay về qua /payment/return/vnpay. */
  const onPayVnpay = useCallback(
    (invoiceId: string) => {
      payMyInvoice.mutate(invoiceId, {
        onSuccess: ({ payUrl }) => window.location.assign(payUrl),
      });
    },
    [payMyInvoice],
  );
  const payingInvoiceId = payMyInvoice.isPending ? (payMyInvoice.variables ?? null) : null;

  return {
    invoices,
    totals,
    courseNameOf,
    onPayVnpay,
    payingInvoiceId,
    payError: payMyInvoice.isError ? payMyInvoice.error : null,
    isPending: invoicesQuery.isPending,
    error: invoicesQuery.isError ? invoicesQuery.error : null,
    isEmpty: invoicesQuery.isSuccess && invoices.length === 0,
  };
}
