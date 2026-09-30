import { cx } from "@/shared/lib/class-names";
import { Square, SquareCheck } from "lucide";
import { MorphIcon } from "morphicons/react";
import type { ReactNode } from "react";

export interface CheckableRowProps {
  readonly checked: boolean;
  readonly onToggle: () => void;
  readonly label: string;
  readonly description?: string;
  readonly trailing?: ReactNode;
}

/** Một dòng chọn/bỏ chọn dùng chung cho danh sách nhóm quyền và danh sách quyền. */
export function CheckableRow({ checked, onToggle, label, description, trailing }: CheckableRowProps) {
  return (
    <div
      className={cx(
        "flex items-center gap-3 rounded-2xl border px-3 py-2 transition-colors",
        checked
          ? "border-orange-300 bg-orange-50/80"
          : "border-slate-200/80 hover:border-orange-200/70 bg-white/60",
      )}
    >
      <button
        type="button"
        role="checkbox"
        aria-checked={checked}
        onClick={onToggle}
        className="flex min-w-0 flex-1 items-center gap-3 text-left"
      >
        <span
          className={cx(
            "flex size-6 shrink-0 items-center justify-center rounded-lg transition-colors",
            checked ? "text-orange-600" : "text-slate-400 hover:text-slate-600",
          )}
        >
          <MorphIcon
            icon={checked ? SquareCheck : Square}
            spring="snappy"
            reducedMotion="user"
            size={18}
            strokeWidth={checked ? 2.2 : 1.75}
            label={checked ? "Đã chọn" : "Chưa chọn"}
          />
        </span>
        <span className="min-w-0">
          <span className="block truncate text-sm font-medium text-slate-800">{label}</span>
          {description ? <span className="block truncate text-xs text-slate-500">{description}</span> : null}
        </span>
      </button>
      {trailing}
    </div>
  );
}
