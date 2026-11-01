import { FullScreenLoader } from "@/app/ui/full-screen-loader";
import { useSession } from "@/entities/account";
import { APP_ROUTE, NEXT_PATH_PARAM, OAUTH_PATH_PREFIX } from "@/shared/constants/app-routes";
import { useEffect } from "react";
import { Navigate, Outlet, useSearchParams } from "react-router-dom";

function resolveSafeOAuthNextPath(rawNext: string | null): string | null {
  if (!rawNext || !rawNext.startsWith(OAUTH_PATH_PREFIX) || rawNext.startsWith("//")) {
    return null;
  }
  return rawNext;
}

/** Đang có phiên thì không cần nhìn trang đăng nhập nữa. */
export function RedirectWhenSignedIn() {
  const session = useSession();
  const [searchParams] = useSearchParams();
  const oauthNextPath = resolveSafeOAuthNextPath(searchParams.get(NEXT_PATH_PARAM));

  useEffect(() => {
    if (session.data && oauthNextPath) {
      window.location.replace(oauthNextPath);
    }
  }, [session.data, oauthNextPath]);

  if (session.isPending) {
    return <FullScreenLoader />;
  }
  if (session.data) {
    if (oauthNextPath) {
      return <FullScreenLoader />;
    }
    return <Navigate to={APP_ROUTE.dashboard} replace />;
  }
  return <Outlet />;
}
