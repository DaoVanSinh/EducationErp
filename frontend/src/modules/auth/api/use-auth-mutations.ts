import { accountKeys } from "@/entities/account";
import {
  authApi,
  type CompleteInvitePayload,
  type LoginPayload,
  type ResetPasswordPayload,
} from "@/modules/auth/api/auth-api";
import { APP_ROUTE, OAUTH_PATH_PREFIX } from "@/shared/constants/app-routes";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useNavigate } from "react-router-dom";

function navigateAfterAuth(
  navigate: ReturnType<typeof useNavigate>,
  redirectTo: string,
): void {
  if (redirectTo.startsWith(OAUTH_PATH_PREFIX) && !redirectTo.startsWith("//")) {
    window.location.replace(redirectTo);
    return;
  }
  navigate(redirectTo, { replace: true });
}

/**
 * Đăng nhập xong thì phiên cũ trong cache không còn đúng nữa. Chờ invalidate hoàn tất rồi mới điều
 * hướng: nếu điều hướng trước, route bảo vệ sẽ đọc phiên cũ (null) và đá ngược về trang đăng nhập. Lần
 * đăng nhập đầu (requiresPasswordChange) không điều hướng - trang login tự chuyển sang form đổi mật khẩu.
 */
export function useLogin(redirectTo: string) {
  const queryClient = useQueryClient();
  const navigate = useNavigate();

  return useMutation({
    mutationFn: (payload: LoginPayload) => authApi.login(payload),
    onSuccess: async (result) => {
      if (result.requiresPasswordChange) {
        return;
      }
      await queryClient.invalidateQueries({ queryKey: accountKeys.session() });
      navigateAfterAuth(navigate, redirectTo);
    },
  });
}

export function useCompleteInvite(redirectTo: string) {
  const queryClient = useQueryClient();
  const navigate = useNavigate();

  return useMutation({
    mutationFn: (payload: CompleteInvitePayload) => authApi.completeInvite(payload),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: accountKeys.session() });
      navigateAfterAuth(navigate, redirectTo);
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
