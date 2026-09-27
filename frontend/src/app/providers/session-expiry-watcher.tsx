import { accountKeys } from "@/entities/account";
import { apiClient } from "@/shared/api/api-client";
import { useQueryClient } from "@tanstack/react-query";
import { useEffect } from "react";

/**
 * Khi refresh cũng không cứu được phiên, xoá sạch cache: dữ liệu của người vừa đăng xuất không được
 * nằm lại để người đăng nhập sau nhìn thấy trong lúc chờ request đầu tiên trả về.
 */
export function SessionExpiryWatcher() {
  const queryClient = useQueryClient();

  useEffect(
    () =>
      apiClient.onSessionExpired(() => {
        queryClient.clear();
        queryClient.setQueryData(accountKeys.session(), null);
      }),
    [queryClient],
  );

  return null;
}
