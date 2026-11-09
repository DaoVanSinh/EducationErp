import { Can } from "@/entities/permission";
import { useTeacherPay, type TeacherPay } from "@/entities/teacher";
import { EditTeacherPayDialog } from "@/modules/teachers/ui/edit-teacher-pay-dialog";
import { PAY_TYPE, PAY_TYPE_LABEL } from "@/shared/constants/pay-type";
import { ACCESS_RULE } from "@/shared/constants/permissions";
import { formatter } from "@/shared/lib/format";
import { useDisclosure } from "@/shared/lib/use-disclosure";
import { EmptyState } from "@/shared/ui/empty-state";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { GlassButton } from "@/shared/ui/glass-button";
import { Skeleton } from "@/shared/ui/skeleton";
import { Pencil, Wallet } from "lucide-react";
import type { ReactNode } from "react";

export interface TeacherPayPanelProps {
  readonly profileId: string;
}

/** Tab Lương của trang chi tiết giáo viên - chỉ hiện với người có quyền xem lương. */
export function TeacherPayPanel({ profileId }: TeacherPayPanelProps) {
  const pay = useTeacherPay(profileId);
  const dialog = useDisclosure();

  if (pay.isError) {
    return <ErrorNotice error={pay.error} />;
  }
  if (pay.isPending) {
    return <Skeleton className="h-24" />;
  }

  return (
    <div className="flex flex-col gap-4">
      {pay.data.payType === null ? (
        <EmptyState
          icon={<Wallet size={28} aria-hidden />}
          title="Chưa thiết lập lương"
          description="Giáo viên này chưa có mức lương nên chưa có phiếu lương. Nếu đã có buổi dạy, kỳ lương sẽ không gửi duyệt được."
        />
      ) : (
        <PayTerms pay={pay.data} />
      )}
      <Can {...ACCESS_RULE.updatePayroll}>
        <div>
          <GlassButton variant="secondary" size="sm" onClick={dialog.onOpen} icon={<Pencil size={14} aria-hidden />}>
            {pay.data.payType === null ? "Thiết lập lương" : "Sửa lương"}
          </GlassButton>
        </div>
      </Can>
      {dialog.open ? <EditTeacherPayDialog pay={pay.data} open onClose={dialog.onClose} /> : null}
    </div>
  );
}

function PayTerms({ pay }: { readonly pay: TeacherPay }) {
  return (
    <dl className="grid grid-cols-1 gap-3 sm:grid-cols-2">
      <Term label="Hình thức trả lương">{pay.payType === null ? "—" : PAY_TYPE_LABEL[pay.payType]}</Term>
      <Term label="Ngày bắt đầu làm việc">{pay.workStartDate === null ? "—" : formatter.date(pay.workStartDate)}</Term>
      {pay.payType === PAY_TYPE.monthly ? <MonthlyTerms pay={pay} /> : <HourlyTerms pay={pay} />}
    </dl>
  );
}

function HourlyTerms({ pay }: { readonly pay: TeacherPay }) {
  return <Term label="Đơn giá một giờ">{formatter.money(pay.hourlyRate ?? 0)}</Term>;
}

function MonthlyTerms({ pay }: { readonly pay: TeacherPay }) {
  const allowances = pay.allowances.map((item) => `${item.name}: ${formatter.money(item.amount)}`).join(", ");
  return (
    <>
      <Term label="Lương tháng">{formatter.money(pay.monthlySalary ?? 0)}</Term>
      <Term label="Hết thử việc">
        {pay.probationEndDate === null ? "Không thử việc" : formatter.date(pay.probationEndDate)}
      </Term>
      <Term label="Số người phụ thuộc">{pay.dependentCount}</Term>
      <Term label="Phụ cấp cố định">{allowances.length === 0 ? "Không có" : allowances}</Term>
    </>
  );
}

function Term({ label, children }: { readonly label: string; readonly children: ReactNode }) {
  return (
    <div className="glass rounded-2xl p-3">
      <dt className="text-xs text-mist-500">{label}</dt>
      <dd className="text-sm text-mist-100">{children}</dd>
    </div>
  );
}
