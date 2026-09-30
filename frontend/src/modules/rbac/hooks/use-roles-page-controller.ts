import { permissionLabel, useRbacCatalog, type PermissionOption } from "@/entities/rbac-catalog";
import type { NamedReference } from "@/shared/api/schemas";
import { useMemo, useState } from "react";

export type RbacTab = "roles" | "groups" | "permissions";

export function useRolesPageController() {
  const catalog = useRbacCatalog();
  const [activeTab, setActiveTab] = useState<RbacTab>("roles");
  const [searchQuery, setSearchQuery] = useState("");
  const [selectedRoleId, setSelectedRoleId] = useState<string | null>(null);
  const [selectedGroupId, setSelectedGroupId] = useState<string | null>(null);
  const [roleDialogOpen, setRoleDialogOpen] = useState(false);
  const [groupDialogOpen, setGroupDialogOpen] = useState(false);

  const query = searchQuery.trim().toLowerCase();

  // Lọc vai trò linh hoạt theo từ khoá tìm kiếm
  const filteredRoles = useMemo(() => {
    const roles = catalog.data?.roles ?? [];
    if (query.length === 0) return roles;
    return roles.filter((role: NamedReference) => role.name.toLowerCase().includes(query));
  }, [catalog.data?.roles, query]);

  // Lọc nhóm quyền linh hoạt theo từ khoá tìm kiếm
  const filteredGroups = useMemo(() => {
    const groups = catalog.data?.permissionGroups ?? [];
    if (query.length === 0) return groups;
    return groups.filter((group: NamedReference) => group.name.toLowerCase().includes(query));
  }, [catalog.data?.permissionGroups, query]);

  // Phân nhóm toàn bộ quyền động theo từng phân hệ (Resource)
  const permissionsByResource = useMemo(() => {
    const all = catalog.data?.permissions ?? [];
    const filtered = query.length === 0
      ? all
      : all.filter((p: PermissionOption) => {
          const label = permissionLabel(p).toLowerCase();
          return (
            label.includes(query) ||
            p.resource.toLowerCase().includes(query) ||
            p.action.toLowerCase().includes(query)
          );
        });

    const map = new Map<string, PermissionOption[]>();
    for (const p of filtered) {
      const existing = map.get(p.resource) ?? [];
      existing.push(p);
      map.set(p.resource, existing);
    }
    return Array.from(map.entries()).map(([resource, items]) => ({ resource, items }));
  }, [catalog.data?.permissions, query]);

  const activeRoleId = selectedRoleId ?? (filteredRoles[0]?.id ?? null);
  const selectedRole = useMemo(() => {
    return catalog.data?.roles.find((r) => r.id === activeRoleId) ?? null;
  }, [catalog.data?.roles, activeRoleId]);

  return {
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
    selectedGroupId,
    setSelectedGroupId,
    permissionsByResource,
    roleDialogOpen,
    groupDialogOpen,
    openRoleDialog: () => setRoleDialogOpen(true),
    closeRoleDialog: () => setRoleDialogOpen(false),
    openGroupDialog: () => setGroupDialogOpen(true),
    closeGroupDialog: () => setGroupDialogOpen(false),
  };
}
