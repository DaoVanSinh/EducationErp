import {
  accountApi,
  accountKeys,
  type ChangePasswordPayload,
  type ProfileUpdatePayload,
} from "@/entities/account";
import { useMutation, useQueryClient } from "@tanstack/react-query";

/** Sửa hồ sơ xong phải lấy lại phiên: tên và ảnh đang hiển thị trên thanh điều hướng là từ đó. */
export function useUpdateProfile() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: ProfileUpdatePayload) => accountApi.updateProfile(payload),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: accountKeys.session() }),
  });
}

/** Tải ảnh đại diện mới lên MinIO hoặc xoá ảnh để quay về chữ cái đầu của tên. */
export function useSaveAvatar() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (image: File | null) => accountApi.saveAvatar(image),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: accountKeys.session() }),
  });
}

/**
 * Đổi mật khẩu thành công thì backend đã đóng mọi phiên khác của tài khoản này. Phiên hiện tại vẫn
 * sống, nên không cần đưa người dùng ra trang đăng nhập.
 */
export function useChangePassword() {
  return useMutation({
    mutationFn: (payload: ChangePasswordPayload) => accountApi.changePassword(payload),
  });
}
