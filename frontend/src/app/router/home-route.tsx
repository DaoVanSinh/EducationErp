import { useLandingPath } from "@/app/router/use-landing-path";
import { APP_ROUTE } from "@/shared/constants/app-routes";
import type { ReactNode } from "react";
import { Navigate } from "react-router-dom";

export interface HomeRouteProps {
  /** Trang Tổng quan - chỉ render khi người dùng thực sự có quyền xem nó. */
  readonly dashboard: ReactNode;
}

/**
 * "/" là trang chủ chung cho mọi role. Trước đây nó luôn là Tổng quan, nên giáo viên/học viên vừa
 * đăng nhập đã gặp màn hình "Bạn không có quyền xem phần này". Giờ ai không có quyền Tổng quan được
 * chuyển thẳng tới trang đầu tiên của họ trên menu.
 */
export function HomeRoute({ dashboard }: HomeRouteProps) {
  const landingPath = useLandingPath();
  if (landingPath === APP_ROUTE.dashboard) {
    return <>{dashboard}</>;
  }
  return <Navigate to={landingPath} replace />;
}
