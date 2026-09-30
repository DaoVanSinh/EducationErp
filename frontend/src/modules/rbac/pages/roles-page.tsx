import { Can, RequirePermission } from "@/entities/permission";
import { useRolesPageController } from "@/modules/rbac/hooks/use-roles-page-controller";
import { CreatePermissionGroupDialog } from "@/modules/rbac/ui/create-permission-group-dialog";
import { CreateRoleDialog } from "@/modules/rbac/ui/create-role-dialog";
import { GroupItemCard } from "@/modules/rbac/ui/groups/group-item-card";
import { ResourcePermissionGroupCard } from "@/modules/rbac/ui/permissions/resource-permission-group-card";
import { RbacTabBar } from "@/modules/rbac/ui/roles/rbac-tab-bar";
import { RoleDetailInspector } from "@/modules/rbac/ui/roles/role-detail-inspector";
import { RoleItemCard } from "@/modules/rbac/ui/roles/role-item-card";
import { RoleKpiBanner } from "@/modules/rbac/ui/roles/role-kpi-banner";
import { ACCESS_RULE } from "@/shared/constants/permissions";
import { EmptyState } from "@/shared/ui/empty-state";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { GlassButton } from "@/shared/ui/glass-button";
import { PageHeader } from "@/shared/ui/page-header";
import { Skeleton } from "@/shared/ui/skeleton";
import { KeyRound, Plus } from "lucide-react";

export function RolesPage() {
  const {
    catalog,
    activeTab,
    setActiveTab,
    searchQuery,
    setSearchQuery,
    filteredRoles,
    activeRoleId,
    selectedRole,
    setSelectedRoleId,
    filteredGroups,
    permissionsByResource,
    roleDialogOpen,
    groupDialogOpen,
    openRoleDialog,
    closeRoleDialog,
    openGroupDialog,
    closeGroupDialog,
  } = useRolesPageController();

  return (
    <RequirePermission {...ACCESS_RULE.readRole}>
      <div className="flex flex-col gap-6">
        <PageHeader
          title="Vai trò & Phân quyền"
          description="Cơ chế phân quyền linh hoạt theo vai trò, nhóm quyền và tài khoản."
          actions={
            <>
              <Can {...ACCESS_RULE.createPermissionGroup}>
                <GlassButton
                  variant="secondary"
                  onClick={openGroupDialog}
                  icon={<KeyRound size={16} aria-hidden />}
                >
                  Nhóm quyền mới
                </GlassButton>
              </Can>
              <Can {...ACCESS_RULE.createRole}>
                <GlassButton onClick={openRoleDialog} icon={<Plus size={16} aria-hidden />}>
                  Vai trò mới
                </GlassButton>
              </Can>
            </>
          }
        />

        {catalog.isError ? <ErrorNotice error={catalog.error} /> : null}

        {catalog.isPending ? (
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <Skeleton className="h-28" />
            <Skeleton className="h-28" />
            <Skeleton className="h-28" />
            <Skeleton className="h-28" />
          </div>
        ) : null}

        {catalog.data ? (
          <>
            <RoleKpiBanner
              totalRoles={catalog.data.roles.length}
              totalGroups={catalog.data.permissionGroups.length}
              totalPermissions={catalog.data.permissions.length}
              totalBranches={catalog.data.branches.length}
            />

            <RbacTabBar
              activeTab={activeTab}
              onTabChange={setActiveTab}
              searchQuery={searchQuery}
              onSearchChange={setSearchQuery}
              roleCount={filteredRoles.length}
              groupCount={filteredGroups.length}
              permissionCount={catalog.data.permissions.length}
            />

            {/* TAB CONTENT: ROLES */}
            {activeTab === "roles" ? (
              <div className="grid grid-cols-1 items-start gap-6 xl:grid-cols-12 min-w-0 w-full">
                <div className="flex flex-col gap-3 xl:col-span-5 min-w-0 w-full">
                  <div className="flex items-center justify-between px-1">
                    <span className="text-xs font-bold tracking-wider text-slate-500 uppercase">
                      Danh sách vai trò ({filteredRoles.length})
                    </span>
                  </div>
                  {filteredRoles.length === 0 ? (
                    <EmptyState title="Không tìm thấy vai trò phù hợp" />
                  ) : (
                    <div className="flex flex-col gap-2.5 min-w-0 w-full">
                      {filteredRoles.map((role) => (
                        <RoleItemCard
                          key={role.id}
                          role={role}
                          isSelected={role.id === activeRoleId}
                          onSelect={setSelectedRoleId}
                        />
                      ))}
                    </div>
                  )}
                </div>

                <div className="xl:col-span-7 min-w-0 w-full">
                  <RoleDetailInspector
                    role={selectedRole}
                    allGroups={catalog.data.permissionGroups}
                  />
                </div>
              </div>
            ) : null}

            {/* TAB CONTENT: PERMISSION GROUPS */}
            {activeTab === "groups" ? (
              <div className="flex flex-col gap-4">
                <div className="flex items-center justify-between px-1">
                  <span className="text-xs font-bold tracking-wider text-slate-500 uppercase">
                    Tất cả nhóm quyền ({filteredGroups.length})
                  </span>
                </div>
                {filteredGroups.length === 0 ? (
                  <EmptyState title="Không tìm thấy nhóm quyền phù hợp" />
                ) : (
                  <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
                    {filteredGroups.map((group) => (
                      <GroupItemCard key={group.id} group={group} />
                    ))}
                  </div>
                )}
              </div>
            ) : null}

            {/* TAB CONTENT: PERMISSIONS */}
            {activeTab === "permissions" ? (
              <div className="flex flex-col gap-4">
                <div className="flex items-center justify-between px-1">
                  <span className="text-xs font-bold tracking-wider text-slate-500 uppercase">
                    Quyền theo phân hệ chức năng ({permissionsByResource.length} phân hệ)
                  </span>
                </div>
                {permissionsByResource.length === 0 ? (
                  <EmptyState title="Không tìm thấy quyền phù hợp" />
                ) : (
                  <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
                    {permissionsByResource.map(({ resource, items }) => (
                      <ResourcePermissionGroupCard
                        key={resource}
                        resource={resource}
                        permissions={items}
                      />
                    ))}
                  </div>
                )}
              </div>
            ) : null}
          </>
        ) : null}

        <CreateRoleDialog open={roleDialogOpen} onClose={closeRoleDialog} />
        <CreatePermissionGroupDialog open={groupDialogOpen} onClose={closeGroupDialog} />
      </div>
    </RequirePermission>
  );
}
