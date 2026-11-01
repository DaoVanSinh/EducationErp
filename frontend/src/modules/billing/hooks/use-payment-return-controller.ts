import { PAYMENT_METHOD, useConfirmVnpayReturn, usePaymentStatus } from "@/entities/billing";
import { PAYMENT_RETURN_GATEWAY_PARAM } from "@/shared/constants/app-routes";
import { useMemo } from "react";
import { useParams, useSearchParams } from "react-router-dom";

const VNPAY_GATEWAY = PAYMENT_METHOD.vnpay.toLowerCase();

/** Tên query param mang mã giao dịch của từng cổng - VNPay dùng vnp_TxnRef, MoMo dùng orderId. */
const TRANSACTION_ID_PARAM: Record<string, string> = {
  [VNPAY_GATEWAY]: "vnp_TxnRef",
  [PAYMENT_METHOD.momo.toLowerCase()]: "orderId",
};

/**
 * Chỉ LẤY mã giao dịch từ URL, không đọc trạng thái từ URL. Mọi kết luận thành công/thất bại đến từ
 * backend - query param là dữ liệu người dùng kiểm soát được (spec mục 10).
 *
 * Với VNPay: gửi nguyên bộ tham số đã ký lên backend xác nhận TRƯỚC (backend kiểm chữ ký rồi xử lý như
 * IPN - đường dự phòng khi IPN sandbox không tới), xong mới bắt đầu poll trạng thái.
 */
export function usePaymentReturnController() {
  const params = useParams<Record<string, string>>();
  const [searchParams] = useSearchParams();

  const gateway = (params[PAYMENT_RETURN_GATEWAY_PARAM] ?? "").toLowerCase();
  const transactionParam = TRANSACTION_ID_PARAM[gateway];
  const gatewayTransactionId =
    transactionParam === undefined ? "" : (searchParams.get(transactionParam) ?? "");

  const signedParams = useMemo(() => Object.fromEntries(searchParams.entries()), [searchParams]);
  const needsConfirmation = gateway === VNPAY_GATEWAY && gatewayTransactionId.length > 0;
  const confirmation = useConfirmVnpayReturn(signedParams, needsConfirmation);
  const isConfirming = needsConfirmation && confirmation.isPending;

  const payment = usePaymentStatus(isConfirming ? "" : gatewayTransactionId);

  return { gateway, gatewayTransactionId, payment };
}
