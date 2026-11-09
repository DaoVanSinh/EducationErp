import { formatter } from "@/shared/lib/format";
import { PAY_TYPE, type PayType } from "@/shared/constants/pay-type";

export interface PayBasis {
  readonly payType: PayType;
  readonly payRate: number;
  readonly sessionCount: number;
  readonly hoursWorked: number | null;
  readonly paidDays: number;
  readonly probationDays: number;
}

/** Số ngày của kỳ, lấy từ ngày cuối kỳ dạng "YYYY-MM-DD". */
export function daysInPeriod(periodEnd: string): number {
  return Number(periodEnd.slice(-2));
}

export function teachingText(basis: PayBasis): string {
  return `${formatter.count(basis.sessionCount)} buổi, ${formatter.count(basis.hoursWorked ?? 0)} giờ dạy`;
}

/** Căn cứ của Lương chính, để người đọc phiếu tự tính lại được. */
export function payBasisText(basis: PayBasis, periodEnd: string): string {
  if (basis.payType === PAY_TYPE.hourly) {
    return `${formatter.count(basis.hoursWorked ?? 0)} giờ x ${formatter.money(basis.payRate)}`;
  }
  const days = `${basis.paidDays}/${daysInPeriod(periodEnd)} ngày x ${formatter.money(basis.payRate)}`;
  return basis.probationDays > 0 ? `${days}, ${basis.probationDays} ngày thử việc 85%` : days;
}
