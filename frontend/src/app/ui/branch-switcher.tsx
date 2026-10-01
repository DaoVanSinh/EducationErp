import type { Session } from "@/entities/account";
import { useSelectedBranch } from "@/entities/branch";
import { usePermissions } from "@/entities/permission";
import { useRbacCatalog } from "@/entities/rbac-catalog";
import { ACCESS_RULE } from "@/shared/constants/permissions";
import { DropdownMenu } from "@/shared/ui/dropdown-menu";
import { Check, ChevronDown } from "lucide-react";
import { useState } from "react";

export interface BranchSwitcherProps {
  readonly session: Session;
}

const ORGANIZATION_WIDE_LABEL = "Toàn tổ chức";

function defaultLabel(session: Session): string {
  return session.branchName ? `Chi nhánh chính · ${session.branchName}` : "Cấp tổ chức";
}

/**
 * Chi nhánh đang chọn để xem (lọc Tài khoản + Dashboard). Chỉ tài khoản có quyền đọc chi nhánh ở cấp
 * tổ chức mới đổi được - người khác thấy đúng nhãn chi nhánh chính của mình như trước, không bấm được.
 */
export function BranchSwitcher({ session }: BranchSwitcherProps) {
  const permissions = usePermissions();
  const canSwitch = permissions.allows(ACCESS_RULE.readBranch);

  if (!canSwitch) {
    return <StaticBranchPill label={defaultLabel(session)} />;
  }

  return <InteractiveBranchSwitcher session={session} />;
}

function StaticBranchPill({ label }: { readonly label: string }) {
  return (
    <div className="flex items-center gap-2 rounded-xl border border-slate-200/70 bg-white/80 px-3 py-1.5 text-xs font-semibold text-slate-700 shadow-2xs">
      <span className="size-2 rounded-full bg-emerald-500 animate-pulse shrink-0" />
      <span className="truncate">{label}</span>
    </div>
  );
}

function InteractiveBranchSwitcher({ session }: BranchSwitcherProps) {
  const [open, setOpen] = useState(false);
  const { selectedBranchId, setSelectedBranchId } = useSelectedBranch();
  const catalog = useRbacCatalog();
  const branches = catalog.data?.branches ?? [];

  const selectedBranch = branches.find((branch) => branch.id === selectedBranchId);
  const label =
    selectedBranchId === null
      ? defaultLabel(session)
      : `Đang xem · ${selectedBranch?.name ?? "(chi nhánh đã xoá)"}`;

  const choose = (branchId: string | null) => {
    setSelectedBranchId(branchId);
    setOpen(false);
  };

  return (
    <div className="relative">
      <button
        type="button"
        onClick={() => setOpen((prev) => !prev)}
        className="flex w-full items-center gap-2 rounded-xl border border-slate-200/70 bg-white/80 px-3 py-1.5 text-xs font-semibold text-slate-700 shadow-2xs transition-colors hover:border-orange-200/80 hover:bg-white cursor-pointer"
      >
        <span className="size-2 rounded-full bg-emerald-500 animate-pulse shrink-0" />
        <span className="min-w-0 flex-1 truncate text-left">{label}</span>
        <ChevronDown size={14} className="shrink-0 text-slate-400" aria-hidden />
      </button>

      <DropdownMenu open={open} onClose={() => setOpen(false)} className="left-0 top-full mt-2 w-full">
        <BranchOption
          label={ORGANIZATION_WIDE_LABEL}
          selected={selectedBranchId === null}
          onSelect={() => choose(null)}
        />
        {branches.length > 0 ? <div className="my-1 h-px bg-slate-100" /> : null}
        {branches.map((branch) => (
          <BranchOption
            key={branch.id}
            label={branch.name}
            selected={branch.id === selectedBranchId}
            onSelect={() => choose(branch.id)}
          />
        ))}
      </DropdownMenu>
    </div>
  );
}

function BranchOption({
  label,
  selected,
  onSelect,
}: {
  readonly label: string;
  readonly selected: boolean;
  readonly onSelect: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onSelect}
      className="flex w-full items-center justify-between gap-2 rounded-xl px-3 py-2 text-left text-sm text-slate-700 transition-colors hover:bg-orange-50/70 cursor-pointer"
    >
      <span className="min-w-0 truncate">{label}</span>
      {selected ? <Check size={14} className="shrink-0 text-orange-600" aria-hidden /> : null}
    </button>
  );
}
