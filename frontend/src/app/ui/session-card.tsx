import { UserAvatar, type Session } from "@/entities/account";
import { useLogout } from "@/modules/auth";
import { LogOut } from "lucide-react";

export interface SessionCardProps {
  readonly session: Session;
  readonly collapsed?: boolean;
}

export function SessionCard({ session, collapsed = false }: SessionCardProps) {
  const logout = useLogout();

  if (collapsed) {
    return (
      <div className="flex flex-col items-center gap-2 pt-2 border-t border-slate-200/70">
        <div className="relative" title={`${session.fullName} (${session.roleName})`}>
          <UserAvatar fullName={session.fullName} avatarUrl={session.avatarUrl} size="sm" />
          <span className="absolute -bottom-0.5 -right-0.5 size-2.5 rounded-full bg-emerald-500 ring-2 ring-white" />
        </div>
        <button
          type="button"
          onClick={() => logout.mutate()}
          disabled={logout.isPending}
          title="Đăng xuất"
          aria-label="Đăng xuất"
          className="flex size-8 shrink-0 items-center justify-center rounded-xl text-slate-400 transition-colors hover:bg-rose-50 hover:text-rose-600 cursor-pointer"
        >
          <LogOut size={15} aria-hidden />
        </button>
      </div>
    );
  }

  return (
    <div className="glass-deep flex items-center justify-between gap-2.5 rounded-2xl border border-slate-200/80 bg-white/90 p-2.5 shadow-xs transition-all hover:border-orange-200/90">
      <div className="flex min-w-0 items-center gap-2.5">
        <div className="relative shrink-0">
          <UserAvatar fullName={session.fullName} avatarUrl={session.avatarUrl} size="sm" />
          <span className="absolute -bottom-0.5 -right-0.5 size-2.5 rounded-full bg-emerald-500 ring-2 ring-white" />
        </div>
        <div className="min-w-0 flex-1">
          <p className="truncate text-xs font-bold text-slate-900" title={session.fullName}>
            {session.fullName}
          </p>
          <p className="truncate text-[11px] font-medium text-orange-600" title={session.roleName}>
            {session.roleName}
          </p>
        </div>
      </div>

      <button
        type="button"
        onClick={() => logout.mutate()}
        disabled={logout.isPending}
        title="Đăng xuất"
        aria-label="Đăng xuất"
        className="flex size-8 shrink-0 items-center justify-center rounded-xl text-slate-400 transition-colors hover:bg-rose-50 hover:text-rose-600 cursor-pointer"
      >
        <LogOut size={15} aria-hidden />
      </button>
    </div>
  );
}
