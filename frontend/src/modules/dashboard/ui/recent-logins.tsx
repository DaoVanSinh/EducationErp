import { UserAvatar } from "@/entities/account";
import type { RecentLogin } from "@/modules/dashboard/model/dashboard-schema";
import { formatter } from "@/shared/lib/format";
import { staggerDelay } from "@/shared/lib/motion";
import { EmptyState } from "@/shared/ui/empty-state";
import { GlassPanel } from "@/shared/ui/glass-panel";
import { m } from "framer-motion";

export function RecentLogins({ rows }: { readonly rows: readonly RecentLogin[] }) {
  return (
    <GlassPanel className="flex flex-col gap-4">
      <h2 className="text-sm font-semibold tracking-wide text-mist-300 uppercase">Đăng nhập gần đây</h2>

      {rows.length === 0 ? (
        <EmptyState title="Chưa có lần đăng nhập nào được ghi nhận" />
      ) : (
        <ul className="flex flex-col divide-y divide-white/8">
          {rows.map((row, index) => (
            <m.li
              key={`${row.accountId}-${row.occurredAt}`}
              initial={{ opacity: 0, x: -8 }}
              animate={{ opacity: 1, x: 0 }}
              transition={staggerDelay(index)}
              className="flex items-center gap-3 py-3"
            >
              <UserAvatar fullName={row.fullName} size="sm" />
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm text-mist-200">{row.fullName}</p>
                <p className="truncate text-xs text-mist-500">{row.email}</p>
              </div>
              <time dateTime={row.occurredAt} className="shrink-0 text-xs text-mist-400">
                {formatter.timeAgo(row.occurredAt)}
              </time>
            </m.li>
          ))}
        </ul>
      )}
    </GlassPanel>
  );
}
