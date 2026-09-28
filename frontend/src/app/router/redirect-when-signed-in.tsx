import { FullScreenLoader } from "@/app/ui/full-screen-loader";
import { useSession } from "@/entities/account";
import { APP_ROUTE } from "@/shared/constants/app-routes";
import { Navigate, Outlet } from "react-router-dom";

/** Đang có phiên thì không cần nhìn trang đăng nhập nữa. */
export function RedirectWhenSignedIn() {
  const session = useSession();

  if (session.isPending) {
    return <FullScreenLoader />;
  }
  if (session.data) {
    return <Navigate to={APP_ROUTE.dashboard} replace />;
  }
  return <Outlet />;
}
