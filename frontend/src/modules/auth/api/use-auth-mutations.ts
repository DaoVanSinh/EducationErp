import { accountKeys } from "@/entities/account";
import { authApi, type LoginPayload, type ResetPasswordPayload } from "@/modules/auth/api/auth-api";
import { APP_ROUTE } from "@/shared/constants/app-routes";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useNavigate } from "react-router-dom";

/**
 * Đăng nhập xong thì phiên cũ trong cache không còn đúng nữa. Chờ invalidate hoàn tất rồi mới điều
 * hướng: nếu điều hướng trước, route bảo vệ sẽ đọc phiên cũ (null) và đá ngược về trang đăng nhập.
 */
export function useLogin(redirectTo: string) {
  const queryClient = useQueryClient();
  const navigate = useNavigate();

  return useMutation({
    mutationFn: (payload: LoginPayload) => authApi.login(payload),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: accountKeys.session() });
      navigate(redirectTo, { replace: true });
    },
  });
}

export function useLogout() {
  const queryClient = useQueryClient();
  const navigate = useNavigate();

  return useMutation({
    mutationFn: () => authApi.logout(),
    // Kể cả khi gọi logout lỗi (mạng hỏng, token đã hết), phía client vẫn phải quên hết dữ liệu của
    // người vừa dùng - máy này có thể là máy dùng chung.
    onSettled: () => {
      queryClient.clear();
      navigate(APP_ROUTE.login, { replace: true });
    },
  });
}

export function useForgotPassword() {
  return useMutation({
    mutationFn: (email: string) => authApi.forgotPassword(email),
  });
}

export function useResetPassword() {
  return useMutation({
    mutationFn: (payload: ResetPasswordPayload) => authApi.resetPassword(payload),
  });
}
