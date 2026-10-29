import {
  permissionLabel,
  useRbacCatalog,
  type PermissionGroupOption,
  type PermissionOption,
} from "@/entities/rbac-catalog";
import { useCreatePermissionGroup, useUpdatePermissionGroup } from "@/modules/rbac/api/use-rbac-mutations";
import { createPermissionGroupFormSchema } from "@/modules/rbac/model/rbac-forms";
import { PERMISSION_SCOPE, type PermissionScope } from "@/shared/constants/permissions";
import { useZodForm } from "@/shared/lib/use-zod-form";
import { useMemo, useState } from "react";

interface PermissionGroupItem {
  readonly permissionId: string;
  readonly scope: PermissionScope;
}

/** Giá trị chọn "bỏ lọc theo phân hệ" — không phải resource thật nào từ backend, chỉ là quy ước của
 * bộ lọc phía UI, nên không nằm trong shared/constants/permissions.ts (chỗ đó chỉ mirror giá trị
 * backend thật có). */
export const RESOURCE_FILTER_ALL = "ALL";

/**
 * Form nhóm quyền dùng chung cho TẠO mới và SỬA (truyền {@code group}). Khi sửa, danh sách (quyền, phạm vi)
 * nạp sẵn từ danh mục và gửi lại TOÀN BỘ - backend đồng bộ theo đúng danh sách gửi lên.
 */
export function useCreatePermissionGroupController({
  onClose,
  group,
}: {
  readonly onClose: () => void;
  readonly group?: PermissionGroupOption;
}) {
  const catalog = useRbacCatalog();
  const createPermissionGroup = useCreatePermissionGroup();
  const updatePermissionGroup = useUpdatePermissionGroup(group?.id ?? "");
  const [searchQuery, setSearchQuery] = useState("");
  const [selectedResource, setSelectedResource] = useState<string>(RESOURCE_FILTER_ALL);

  const form = useZodForm({
    schema: createPermissionGroupFormSchema,
    initialValues: {
      name: group?.name ?? "",
      description: group?.description ?? "",
      items: (group?.items ?? []).map((item) => ({ permissionId: item.permissionId, scope: item.scope })),
    },
    onSubmit: async (values) => {
      if (group === undefined) {
        await createPermissionGroup.mutateAsync(values);
        form.reset();
      } else {
        await updatePermissionGroup.mutateAsync(values);
      }
      onClose();
    },
  });

  const allPermissions = useMemo(() => catalog.data?.permissions ?? [], [catalog.data?.permissions]);

  // Danh sách các phân hệ duy nhất
  const resources = useMemo(() => {
    const set = new Set<string>();
    for (const p of allPermissions) {
      set.add(p.resource);
    }
    return Array.from(set).sort();
  }, [allPermissions]);

  const query = searchQuery.trim().toLowerCase();

  // Lọc quyền theo từ khoá và phân hệ đã chọn
  const filteredPermissions = useMemo(() => {
    return allPermissions.filter((p: PermissionOption) => {
      if (selectedResource !== RESOURCE_FILTER_ALL && p.resource !== selectedResource) {
        return false;
      }
      if (query.length === 0) return true;
      const label = permissionLabel(p).toLowerCase();
      return (
        label.includes(query) ||
        p.resource.toLowerCase().includes(query) ||
        p.action.toLowerCase().includes(query)
      );
    });
  }, [allPermissions, selectedResource, query]);

  const items = form.values.items as readonly PermissionGroupItem[];
  const scopeOf = (permissionId: string): PermissionScope | undefined =>
    items.find((item: PermissionGroupItem) => item.permissionId === permissionId)?.scope;

  const togglePermission = (permission: PermissionOption) => {
    form.setValue(
      "items",
      scopeOf(permission.id) === undefined
        ? [...items, { permissionId: permission.id, scope: PERMISSION_SCOPE.branch }]
        : items.filter((item: PermissionGroupItem) => item.permissionId !== permission.id),
    );
  };

  const changeScope = (permissionId: string, scope: PermissionScope) => {
    form.setValue(
      "items",
      items.map((item: PermissionGroupItem) =>
        item.permissionId === permissionId ? { ...item, scope } : item,
      ),
    );
  };

  return {
    isEditing: group !== undefined,
    values: form.values,
    fieldErrors: form.fieldErrors,
    submitError: form.submitError,
    isSubmitting: form.isSubmitting,
    permissions: allPermissions,
    filteredPermissions,
    resources,
    searchQuery,
    setSearchQuery,
    selectedResource,
    setSelectedResource,
    items,
    scopeOf,
    togglePermission,
    changeScope,
    handleSubmit: form.handleSubmit,
    setName: (name: string) => form.setValue("name", name),
    setDescription: (description: string) => form.setValue("description", description),
  };
}
