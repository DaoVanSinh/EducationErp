import { NAV_ITEMS } from "@/app/layouts/nav-items";
import { usePermissions } from "@/entities/permission";
import { APP_ROUTE } from "@/shared/constants/app-routes";

/**
 * Trang đầu tiên trong menu mà người dùng có quyền mở - nơi họ được đưa tới khi vào "/". Admin có
 * quyền Tổng quan nên ở lại "/", giáo viên tới "Lớp của tôi", học viên tới "Học tập của tôi". Không
 * khớp gì thì về "Hồ sơ của tôi" - trang ai đăng nhập cũng mở được.
 */
export function useLandingPath(): string {
  const permissions = usePermissions();
  const firstAllowed = NAV_ITEMS.find(
    (item) => item.requirement === undefined || permissions.allows(item.requirement),
  );
  return firstAllowed?.path ?? APP_ROUTE.profile;
}
