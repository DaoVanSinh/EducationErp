import { Can, RequirePermission } from "@/entities/permission";
import { permissionLabel, useRbacCatalog } from "@/entities/rbac-catalog";
import { CreatePermissionGroupDialog } from "@/modules/rbac/ui/create-permission-group-dialog";
import { CreateRoleDialog } from "@/modules/rbac/ui/create-role-dialog";
import type { NamedReference } from "@/shared/api/schemas";
import { ACCESS_RULE } from "@/shared/constants/permissions";
import { staggerDelay } from "@/shared/lib/motion";
import { Badge } from "@/shared/ui/badge";
import { EmptyState } from "@/shared/ui/empty-state";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassPanel } from "@/shared/ui/glass-panel";
import { PageHeader } from "@/shared/ui/page-header";
import { Skeleton } from "@/shared/ui/skeleton";
import { m } from "framer-motion";
import { KeySquare, Plus } from "lucide-react";
import { useState } from "react";

function ReferenceList({ rows, emptyTitle }: { readonly rows: readonly NamedReference[]; readonly emptyTitle: string }) {
  if (rows.length === 0) {
    return <EmptyState title={emptyTitle} />;
  }
  return (
    <ul className="flex flex-col gap-2">
      {rows.map((row, index) => (
        <m.li
          key={row.id}
          initial={{ opacity: 0, x: -6 }}
          animate={{ opacity: 1, x: 0 }}
          transition={staggerDelay(index)}
          className="rounded-2xl border border-white/10 px-3 py-2 text-sm text-mist-200"
        >
          {row.name}
        </m.li>
      ))}
    </ul>
  );
}

export function RolesPage() {
  const catalog = useRbacCatalog();
  const [roleDialogOpen, setRoleDialogOpen] = useState(false);
  const [groupDialogOpen, setGroupDialogOpen] = useState(false);

  return (
    <RequirePermission {...ACCESS_RULE.readRole}>
      <div className="flex flex-col gap-6">
        <PageHeader
          title="Vai trò và quyền"
          description="Quyền được gom thành nhóm quyền, nhóm quyền gắn vào vai trò và nhóm người dùng."
          actions={
            <>
              <Can {...ACCESS_RULE.createPermissionGroup}>
                <GlassButton
                  variant="secondary"
                  onClick={() => setGroupDialogOpen(true)}
                  icon={<KeySquare size={16} aria-hidden />}
                >
                  Nhóm quyền mới
                </GlassButton>
              </Can>
              <Can {...ACCESS_RULE.createRole}>
                <GlassButton onClick={() => setRoleDialogOpen(true)} icon={<Plus size={16} aria-hidden />}>
                  Vai trò mới
                </GlassButton>
              </Can>
            </>
          }
        />

        {catalog.isError ? <ErrorNotice error={catalog.error} /> : null}

        {catalog.isPending ? (
          <div className="grid gap-4 lg:grid-cols-3">
            <Skeleton className="h-64" />
            <Skeleton className="h-64" />
            <Skeleton className="h-64" />
          </div>
        ) : null}

        {catalog.data ? (
          <div className="grid gap-4 lg:grid-cols-3">
            <GlassPanel className="flex flex-col gap-4">
              <h2 className="text-sm font-semibold tracking-wide text-mist-300 uppercase">Vai trò</h2>
              <ReferenceList rows={catalog.data.roles} emptyTitle="Chưa có vai trò nào" />
            </GlassPanel>

            <GlassPanel className="flex flex-col gap-4">
              <h2 className="text-sm font-semibold tracking-wide text-mist-300 uppercase">Nhóm quyền</h2>
              <ReferenceList rows={catalog.data.permissionGroups} emptyTitle="Chưa có nhóm quyền nào" />
            </GlassPanel>

            <GlassPanel className="flex flex-col gap-4">
              <h2 className="text-sm font-semibold tracking-wide text-mist-300 uppercase">
                Quyền có trong hệ thống
              </h2>
              <div className="flex flex-wrap gap-2">
                {catalog.data.permissions.map((permission) => (
                  <Badge key={permission.id}>{permissionLabel(permission)}</Badge>
                ))}
              </div>
            </GlassPanel>
          </div>
        ) : null}

        <CreateRoleDialog open={roleDialogOpen} onClose={() => setRoleDialogOpen(false)} />
        <CreatePermissionGroupDialog open={groupDialogOpen} onClose={() => setGroupDialogOpen(false)} />
      </div>
    </RequirePermission>
  );
}
