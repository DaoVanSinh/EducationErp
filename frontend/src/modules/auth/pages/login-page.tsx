import { useLoginFormController } from "@/modules/auth/hooks/use-login-form-controller";
import { CompleteInviteForm } from "@/modules/auth/ui/complete-invite-form";
import { LoginForm } from "@/modules/auth/ui/login-form";
import { APP_ROUTE, NEXT_PATH_PARAM, OAUTH_PATH_PREFIX } from "@/shared/constants/app-routes";
import { BrandLogoIcon } from "@/shared/ui/brand-logo";
import { Link, useLocation, useSearchParams } from "react-router-dom";

interface RedirectState {
  readonly from?: string;
}

function resolveLoginRedirectTarget(
  fromState: string | undefined,
  nextQuery: string | null,
): string {
  if (fromState) {
    return fromState;
  }
  if (nextQuery && nextQuery.startsWith(OAUTH_PATH_PREFIX) && !nextQuery.startsWith("//")) {
    return nextQuery;
  }
  return APP_ROUTE.dashboard;
}

/**
 * Trang đăng nhập DS Academy:
 * Hiển thị logo PNG DS Academy ở đỉnh, tiêu đề Đăng nhập căn giữa,
 * form nhập liệu sạch sẽ và dòng liên hệ chân thẻ.
 */
export function LoginPage() {
  const location = useLocation();
  const [searchParams] = useSearchParams();
  const state = location.state as RedirectState | null;
  const redirectTo = resolveLoginRedirectTarget(state?.from, searchParams.get(NEXT_PATH_PARAM));

  const loginController = useLoginFormController(redirectTo);

  return (
    <div className="flex flex-col gap-7">
      <div className="flex flex-col items-center text-center">
        <BrandLogoIcon size="lg" className="mb-3" />
        <h1 className="text-[26px] font-bold tracking-tight text-[#334155]">
          {loginController.requiresPasswordChange ? "Đặt mật khẩu mới" : "Đăng nhập"}
        </h1>
        <p className="mt-1 text-xs text-[#94a3b8]">
          {loginController.requiresPasswordChange
            ? "Vui lòng cập nhật mật khẩu mới để kích hoạt tài khoản DS Academy"
            : "Vui lòng đăng nhập để truy cập vào tài khoản DS Academy của bạn"}
        </p>
      </div>

      {loginController.requiresPasswordChange ? (
        <CompleteInviteForm
          email={loginController.email}
          temporaryPassword={loginController.temporaryPassword}
          redirectTo={redirectTo}
        />
      ) : (
        <LoginForm controller={loginController} />
      )}

      <div className="border-t border-slate-100 pt-5 text-center text-[11px] leading-relaxed text-[#94a3b8]">
        <p>Nếu bạn có bất kỳ thắc mắc nào về khoá học hoặc tài khoản, vui lòng xem tại</p>
        <p className="mt-0.5 font-bold text-[#334155]">
          <Link to={APP_ROUTE.landing} className="text-[#ea580c] hover:underline">
            Trang chủ DS Academy
          </Link>{" "}
          hoặc liên hệ bộ phận tuyển sinh.
        </p>
      </div>
    </div>
  );
}
