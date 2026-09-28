import { RequirePermission } from "@/entities/permission";
import { useDashboardStats } from "@/modules/dashboard/api/use-dashboard-stats";
import { DASHBOARD_STAT_CARDS } from "@/modules/dashboard/model/dashboard-schema";
import { RecentLogins } from "@/modules/dashboard/ui/recent-logins";
import { RoleBreakdown } from "@/modules/dashboard/ui/role-breakdown";
import { StatCard } from "@/modules/dashboard/ui/stat-card";
import { ACCESS_RULE } from "@/shared/constants/permissions";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { PageHeader } from "@/shared/ui/page-header";
import { Skeleton } from "@/shared/ui/skeleton";
import { Building2, ShieldOff, UserCheck, Users } from "lucide-react";
import type { ReactNode } from "react";

/** Icon cho từng ô số liệu, khoá theo cùng key với DASHBOARD_STAT_CARDS. */
const STAT_ICON: Record<string, ReactNode> = {
  totalAccounts: <Users size={18} aria-hidden />,
  activeAccounts: <UserCheck size={18} aria-hidden />,
  disabledAccounts: <ShieldOff size={18} aria-hidden />,
  branchCount: <Building2 size={18} aria-hidden />,
};

export function DashboardPage() {
  const stats = useDashboardStats();

  return (
    <RequirePermission {...ACCESS_RULE.readDashboard}>
      <div className="flex flex-col gap-6">
        <PageHeader title="Tổng quan" description="Tình hình tài khoản và truy cập của toàn hệ thống." />

        {stats.isError ? <ErrorNotice error={stats.error} /> : null}

        {stats.isPending ? (
          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
            {DASHBOARD_STAT_CARDS.map((card) => (
              <Skeleton key={card.key} className="h-32" />
            ))}
          </div>
        ) : null}

        {stats.data ? (
          <>
            <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
              {DASHBOARD_STAT_CARDS.map((card, index) => (
                <StatCard
                  key={card.key}
                  index={index}
                  label={card.label}
                  hint={card.hint}
                  value={stats.data[card.key]}
                  icon={STAT_ICON[card.key]}
                />
              ))}
            </div>

            <div className="grid gap-4 lg:grid-cols-2">
              <RoleBreakdown rows={stats.data.accountsByRole} totalAccounts={stats.data.totalAccounts} />
              <RecentLogins rows={stats.data.recentLogins} />
            </div>
          </>
        ) : null}
      </div>
    </RequirePermission>
  );
}
