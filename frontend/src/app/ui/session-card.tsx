import { UserAvatar, type Session } from "@/entities/account";
import { useLogout } from "@/modules/auth";
import { GlassButton } from "@/shared/ui/glass-button";
import { LogOut } from "lucide-react";

/** Danh tính của người đang đăng nhập, cùng nút đăng xuất - đặt cạnh nhau ở chân thanh điều hướng. */
export function SessionCard({ session }: { readonly session: Session }) {
  const logout = useLogout();

  return (
    <div className="flex flex-col gap-3 border-t border-white/10 pt-4">
      <div className="flex items-center gap-3">
        <UserAvatar fullName={session.fullName} avatarUrl={session.avatarUrl} size="sm" />
        <div className="min-w-0">
          <p className="truncate text-sm text-mist-100">{session.fullName}</p>
          <p className="truncate text-xs text-mist-500">{session.roleName}</p>
        </div>
      </div>
      <GlassButton
        variant="ghost"
        size="sm"
        loading={logout.isPending}
        onClick={() => logout.mutate()}
        icon={<LogOut size={14} aria-hidden />}
      >
        Đăng xuất
      </GlassButton>
    </div>
  );
}
