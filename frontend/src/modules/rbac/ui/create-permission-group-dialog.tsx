import { permissionLabel } from "@/entities/rbac-catalog";
import {
  RESOURCE_FILTER_ALL,
  useCreatePermissionGroupController,
} from "@/modules/rbac/hooks/use-create-permission-group-controller";
import { PermissionScopeRow } from "@/modules/rbac/ui/groups/permission-scope-row";
import { RESOURCE_LABEL } from "@/shared/constants/permissions";
import { cx } from "@/shared/lib/class-names";
import { EmptyState } from "@/shared/ui/empty-state";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { FormField } from "@/shared/ui/form-field";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassInput } from "@/shared/ui/glass-input";
import { GlassModal } from "@/shared/ui/glass-modal";
import { KeyRound, Search } from "lucide-react";

export interface CreatePermissionGroupDialogProps {
  readonly open: boolean;
  readonly onClose: () => void;
}

export function CreatePermissionGroupDialog({ open, onClose }: CreatePermissionGroupDialogProps) {
  const {
    values,
    fieldErrors,
    submitError,
    isSubmitting,
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
    handleSubmit,
    setName,
    setDescription,
  } = useCreatePermissionGroupController({ onClose });

  return (
    <GlassModal
      open={open}
      onClose={onClose}
      title="Tạo nhóm quyền"
      description="Gom các quyền hạn nghiệp vụ thành nhóm để gán linh hoạt cho vai trò và tài khoản."
      icon={<KeyRound size={22} aria-hidden />}
      className="max-w-3xl"
    >
      <form
        id="create-permission-group-form"
        onSubmit={handleSubmit}
        className="flex flex-col gap-5"
        noValidate
      >
        {submitError ? <ErrorNotice error={submitError} /> : null}

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <FormField label="Tên nhóm quyền" htmlFor="permission-group-name" error={fieldErrors.name}>
            <GlassInput
              id="permission-group-name"
              autoFocus
              placeholder="Ví dụ: Quản lý học vụ, Kế toán..."
              value={values.name}
              invalid={fieldErrors.name !== undefined}
              onChange={(event) => setName(event.target.value)}
            />
          </FormField>

          <FormField
            label="Mô tả"
            htmlFor="permission-group-description"
            hint="Mục đích và trách nhiệm của nhóm quyền."
          >
            <GlassInput
              id="permission-group-description"
              placeholder="Không bắt buộc..."
              value={values.description}
              onChange={(event) => setDescription(event.target.value)}
            />
          </FormField>
        </div>

        {/* Permission Selection Section */}
        <div className="flex flex-col gap-3 rounded-3xl border border-slate-200/80 bg-slate-50/50 p-4">
          <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
            <div className="flex items-center gap-2">
              <span className="text-xs font-bold uppercase tracking-wider text-slate-700">
                Danh sách quyền hạn
              </span>
              <span className="rounded-full bg-orange-100/80 px-2.5 py-0.5 text-xs font-bold text-orange-700">
                Đã chọn {items.length} quyền
              </span>
            </div>

            {/* Quick Search */}
            <div className="relative w-full sm:w-64">
              <GlassInput
                type="text"
                placeholder="Tìm quyền..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                className="h-9 pl-9 text-xs"
              />
              <Search
                size={14}
                className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-slate-400"
                aria-hidden
              />
            </div>
          </div>

          {/* Category Filter Pills */}
          <div className="flex items-center gap-1.5 overflow-x-auto pb-1 scrollbar-none">
            <button
              type="button"
              onClick={() => setSelectedResource(RESOURCE_FILTER_ALL)}
              className={cx(
                "rounded-xl px-3 py-1 text-xs font-semibold whitespace-nowrap transition-colors cursor-pointer",
                selectedResource === RESOURCE_FILTER_ALL
                  ? "bg-slate-900 text-white shadow-2xs"
                  : "bg-white/80 text-slate-600 hover:bg-white hover:text-slate-900 border border-slate-200/80",
              )}
            >
              Tất cả
            </button>
            {resources.map((res) => {
              const isSelected = selectedResource === res;
              const label = RESOURCE_LABEL[res] ?? res;
              return (
                <button
                  key={res}
                  type="button"
                  onClick={() => setSelectedResource(res)}
                  className={cx(
                    "rounded-xl px-3 py-1 text-xs font-semibold whitespace-nowrap transition-colors cursor-pointer",
                    isSelected
                      ? "bg-slate-900 text-white shadow-2xs"
                      : "bg-white/80 text-slate-600 hover:bg-white hover:text-slate-900 border border-slate-200/80",
                  )}
                >
                  {label}
                </button>
              );
            })}
          </div>

          {fieldErrors.items ? (
            <p className="text-xs font-medium text-rose-600">{fieldErrors.items}</p>
          ) : null}

          {/* Filtered Permission List */}
          <div className="flex max-h-80 flex-col gap-2 overflow-y-auto pr-1">
            {filteredPermissions.length === 0 ? (
              <EmptyState title="Không tìm thấy quyền phù hợp" />
            ) : (
              filteredPermissions.map((permission) => {
                const scope = scopeOf(permission.id);
                return (
                  <PermissionScopeRow
                    key={permission.id}
                    permission={permission}
                    label={permissionLabel(permission)}
                    checked={scope !== undefined}
                    scope={scope}
                    onToggle={() => togglePermission(permission)}
                    onChangeScope={(newScope) => changeScope(permission.id, newScope)}
                  />
                );
              })
            )}
          </div>
        </div>
      </form>

      <footer className="flex justify-end gap-2.5">
        <GlassButton variant="ghost" onClick={onClose}>
          Huỷ
        </GlassButton>
        <GlassButton type="submit" form="create-permission-group-form" loading={isSubmitting}>
          Tạo nhóm quyền
        </GlassButton>
      </footer>
    </GlassModal>
  );
}
