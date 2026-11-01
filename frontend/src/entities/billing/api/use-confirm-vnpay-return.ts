import { billingApi } from "@/entities/billing/api/billing-api";
import { billingKeys } from "@/entities/billing/api/billing-keys";
import { useQuery } from "@tanstack/react-query";

/**
 * Gửi MỘT lần bộ tham số VNPay đã ký trên Return URL để backend xác nhận (kiểm chữ ký rồi xử lý như
 * IPN). Không retry: chữ ký sai thì gửi lại cũng sai, còn lỗi mạng thì trang vẫn poll trạng thái như cũ.
 * Khoá theo nguyên query string nên F5 không gửi lại trong cùng phiên.
 */
export function useConfirmVnpayReturn(signedParams: Readonly<Record<string, string>>, enabled: boolean) {
  const signedQuery = new URLSearchParams(signedParams).toString();
  return useQuery({
    queryKey: billingKeys.vnpayReturn(signedQuery),
    enabled,
    retry: false,
    staleTime: Number.POSITIVE_INFINITY,
    queryFn: () => billingApi.confirmVnpayReturn({ ...signedParams }),
  });
}
