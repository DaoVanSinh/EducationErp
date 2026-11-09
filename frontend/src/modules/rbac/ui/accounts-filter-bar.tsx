import { ACCOUNT_STATUS, ACCOUNT_STATUS_LABEL, type AccountStatus } from "@/entities/account";
import { GlassInput } from "@/shared/ui/glass-input";
import { GlassSelect } from "@/shared/ui/glass-select";

const STATUS_OPTIONS: readonly AccountStatus[] = Object.values(ACCOUNT_STATUS);

export interface AccountsFilterBarProps {
  readonly queryInput: string;
  readonly onQueryChange: (value: string) => void;
  readonly roleCode: string;
  readonly onRoleChange: (value: string) => void;
  readonly roleOptions: readonly { readonly code: string; readonly name: string }[];
  readonly status: string;
  readonly onStatusChange: (value: string) => void;
}

/** Lọc theo chi nhánh dùng ô chọn cơ sở trên thanh đầu trang, giống mọi danh sách khác. */
export function AccountsFilterBar({
  queryInput,
  onQueryChange,
  roleCode,
  onRoleChange,
  roleOptions,
  status,
  onStatusChange,
}: AccountsFilterBarProps) {
  return (
    <div className="flex flex-wrap items-center gap-2">
      <div className="min-w-56 flex-1">
        <GlassInput
          aria-label="Tìm theo tên hoặc email"
          placeholder="Tìm theo tên hoặc email"
          value={queryInput}
          onChange={(event) => onQueryChange(event.target.value)}
        />
      </div>
      <GlassSelect aria-label="Lọc theo vai trò" value={roleCode} onChange={(event) => onRoleChange(event.target.value)}>
        <option value="">Mọi vai trò</option>
        {roleOptions.map((role) => (
          <option key={role.code} value={role.code}>
            {role.name}
          </option>
        ))}
      </GlassSelect>
      <GlassSelect aria-label="Lọc theo trạng thái" value={status} onChange={(event) => onStatusChange(event.target.value)}>
        <option value="">Mọi trạng thái</option>
        {STATUS_OPTIONS.map((option) => (
          <option key={option} value={option}>
            {ACCOUNT_STATUS_LABEL[option]}
          </option>
        ))}
      </GlassSelect>
    </div>
  );
}
