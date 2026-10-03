import { PAYMENT_STATUS, PAYMENT_STATUS_LABEL } from "@/entities/billing";
import { usePaymentReturnController } from "@/modules/billing/hooks/use-payment-return-controller";
import { APP_ROUTE } from "@/shared/constants/app-routes";
import { formatter } from "@/shared/lib/format";
import { EmptyState } from "@/shared/ui/empty-state";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassPanel } from "@/shared/ui/glass-panel";
import { Skeleton } from "@/shared/ui/skeleton";
import { CheckCircle2, Clock, FileQuestion, XCircle } from "lucide-react";
import { Link } from "react-router-dom";

/**
 * Trang phụ huynh thấy sau khi quay về từ MoMo/VNPay. Public: không bọc trong RequireAuth, không gọi
 * endpoint nào cần quyền. Trạng thái LUÔN đọc lại từ API (IPN là nguồn sự thật), không suy từ query
 * param trên URL (spec mục 10).
 */
export function PaymentReturnPage() {
  const controller = usePaymentReturnController();

  return (
    <GlassPanel className="flex w-full max-w-md flex-col gap-4">
      <h1 className="text-base font-semibold text-mist-100">Kết quả thanh toán học phí</h1>
      <PaymentReturnBody controller={controller} />
      <Link to={APP_ROUTE.dashboard}>
        <GlassButton variant="secondary" size="sm">
          Về trang chủ
        </GlassButton>
      </Link>
    </GlassPanel>
  );
}

function PaymentReturnBody({
  controller,
}: {
  readonly controller: ReturnType<typeof usePaymentReturnController>;
}) {
  if (controller.gatewayTransactionId.length === 0) {
    return (
      <EmptyState
        icon={<FileQuestion size={28} aria-hidden />}
        title="Không đọc được mã giao dịch"
        description="Đường dẫn quay về thiếu mã giao dịch. Vui lòng liên hệ trung tâm để được đối soát."
      />
    );
  }
  if (controller.payment.isPending) {
    return <Skeleton className="h-24" />;
  }
  if (controller.payment.isError) {
    return <ErrorNotice error={controller.payment.error} />;
  }
  if (!controller.payment.data) {
    return (
      <EmptyState
        icon={<FileQuestion size={28} aria-hidden />}
        title="Chưa có dữ liệu giao dịch"
        description="Vui lòng chờ một lát rồi tải lại trang."
      />
    );
  }
  return <PaymentOutcome amount={controller.payment.data.amount} status={controller.payment.data.status} />;
}

const OUTCOME_ICON = {
  [PAYMENT_STATUS.pending]: <Clock size={28} aria-hidden />,
  [PAYMENT_STATUS.success]: <CheckCircle2 size={28} aria-hidden />,
  [PAYMENT_STATUS.failed]: <XCircle size={28} aria-hidden />,
} as const;

const OUTCOME_DESCRIPTION = {
  [PAYMENT_STATUS.pending]:
    "Cổng thanh toán đang xác nhận. Trang sẽ tự cập nhật, bạn không cần thanh toán lại.",
  [PAYMENT_STATUS.success]: "Trung tâm đã ghi nhận khoản thanh toán này vào hoá đơn của bạn.",
  [PAYMENT_STATUS.failed]: "Giao dịch không thành công. Bạn có thể thử lại hoặc liên hệ trung tâm.",
} as const;

function PaymentOutcome({
  amount,
  status,
}: {
  readonly amount: number;
  readonly status: keyof typeof OUTCOME_ICON;
}) {
  return (
    <EmptyState
      icon={OUTCOME_ICON[status]}
      title={`${PAYMENT_STATUS_LABEL[status]} — ${formatter.count(amount)} đ`}
      description={OUTCOME_DESCRIPTION[status]}
    />
  );
}
