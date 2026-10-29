import { usePermissions } from "@/entities/permission";
import {
  permissionLabel,
  useRbacCatalog,
  type PermissionGroupOption,
  type PermissionOption,
  type RoleOption,
} from "@/entities/rbac-catalog";
import { ACCESS_RULE } from "@/shared/constants/permissions";
import { useMemo, useState } from "react";

export type RbacTab = "roles" | "groups" | "permissions";

export function useRolesPageController() {
  const catalog = useRbacCatalog();
  const permissions = usePermissions();
  const [activeTab, setActiveTab] = useState<RbacTab>("roles");
  const [searchQuery, setSearchQuery] = useState("");
  const [selectedRoleId, setSelectedRoleId] = useState<string | null>(null);
  const [roleDialogOpen, setRoleDialogOpen] = useState(false);
  const [groupDialogOpen, setGroupDialogOpen] = useState(false);
  const [editingRole, setEditingRole] = useState<RoleOption | null>(null);
  const [editingGroup, setEditingGroup] = useState<PermissionGroupOption | null>(null);

  const query = searchQuery.trim().toLowerCase();
  const allRoles = useMemo(() => catalog.data?.roles ?? [], [catalog.data?.roles]);
  const allGroups = useMemo(() => catalog.data?.permissionGroups ?? [], [catalog.data?.permissionGroups]);

  const filteredRoles = useMemo(
    () => (query.length === 0 ? allRoles : allRoles.filter((role) => role.name.toLowerCase().includes(query))),
    [allRoles, query],
  );

  const filteredGroups = useMemo(
    () => (query.length === 0 ? allGroups : allGroups.filter((group) => group.name.toLowerCase().includes(query))),
    [allGroups, query],
  );

  // Phân nhóm toàn bộ quyền theo từng phân hệ (Resource)
  const permissionsByResource = useMemo(() => {
    const all = catalog.data?.permissions ?? [];
    const filtered =
      query.length === 0
        ? all
        : all.filter((p: PermissionOption) => {
            const label = permissionLabel(p).toLowerCase();
            return label.includes(query) || p.resource.toLowerCase().includes(query) || p.action.toLowerCase().includes(query);
          });
    const map = new Map<string, PermissionOption[]>();
    for (const p of filtered) {
      map.set(p.resource, [...(map.get(p.resource) ?? []), p]);
    }
    return Array.from(map.entries()).map(([resource, items]) => ({ resource, items }));
  }, [catalog.data?.permissions, query]);

  const activeRoleId = selectedRoleId ?? filteredRoles[0]?.id ?? null;
  const selectedRole = useMemo(() => allRoles.find((role) => role.id === activeRoleId) ?? null, [allRoles, activeRoleId]);
  // Nhóm quyền MÀ VAI TRÒ ĐANG GỒM - trước đây khung chi tiết liệt kê mọi nhóm quyền của hệ thống.
  const selectedRoleGroups = useMemo(
    () => allGroups.filter((group) => selectedRole?.permissionGroupIds.includes(group.id) ?? false),
    [allGroups, selectedRole],
  );

  return {
    catalog,
    activeTab,
    setActiveTab,
    searchQuery,
    setSearchQuery,
    filteredRoles,
    activeRoleId,
    selectedRole,
    selectedRoleGroups,
    setSelectedRoleId,
    filteredGroups,
    permissionsByResource,
    canEditRole: permissions.allows(ACCESS_RULE.updateRole),
    canEditGroup: permissions.allows(ACCESS_RULE.updatePermissionGroup),
    roleDialogOpen,
    groupDialogOpen,
    openRoleDialog: () => setRoleDialogOpen(true),
    closeRoleDialog: () => setRoleDialogOpen(false),
    openGroupDialog: () => setGroupDialogOpen(true),
    closeGroupDialog: () => setGroupDialogOpen(false),
    editingRole,
    startEditingRole: setEditingRole,
    stopEditingRole: () => setEditingRole(null),
    editingGroup,
    startEditingGroup: setEditingGroup,
    stopEditingGroup: () => setEditingGroup(null),
  };
}
