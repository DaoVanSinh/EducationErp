import { PAYMENT_METHOD, usePaymentStatus } from "@/entities/billing";
import { PAYMENT_RETURN_GATEWAY_PARAM } from "@/shared/constants/app-routes";
import { useParams, useSearchParams } from "react-router-dom";

/** Tên query param mang mã giao dịch của từng cổng - VNPay dùng vnp_TxnRef, MoMo dùng orderId. */
const TRANSACTION_ID_PARAM: Record<string, string> = {
  [PAYMENT_METHOD.vnpay.toLowerCase()]: "vnp_TxnRef",
  [PAYMENT_METHOD.momo.toLowerCase()]: "orderId",
};

/**
 * Chỉ LẤY mã giao dịch từ URL, không đọc trạng thái từ URL. Mọi kết luận thành công/thất bại đến từ
 * {@code usePaymentStatus} (dữ liệu IPN đã xác nhận ở backend) - query param là dữ liệu người dùng
 * kiểm soát được (spec mục 10).
 */
export function usePaymentReturnController() {
  const params = useParams<Record<string, string>>();
  const [searchParams] = useSearchParams();

  const gateway = (params[PAYMENT_RETURN_GATEWAY_PARAM] ?? "").toLowerCase();
  const transactionParam = TRANSACTION_ID_PARAM[gateway];
  const gatewayTransactionId =
    transactionParam === undefined ? "" : (searchParams.get(transactionParam) ?? "");

  const payment = usePaymentStatus(gatewayTransactionId);

  return { gateway, gatewayTransactionId, payment };
}
