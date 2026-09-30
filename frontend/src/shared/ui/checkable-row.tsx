import { cx } from "@/shared/lib/class-names";
import { Check } from "lucide-react";
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
        checked ? "border-aqua-400/40 bg-aqua-500/10" : "border-white/10 hover:border-white/20",
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
            "flex size-5 shrink-0 items-center justify-center rounded-md border",
            checked ? "border-aqua-400 bg-aqua-400/80 text-ink-950" : "border-white/25",
          )}
        >
          {checked ? <Check size={14} aria-hidden /> : null}
        </span>
        <span className="min-w-0">
          <span className="block truncate text-sm text-mist-200">{label}</span>
          {description ? <span className="block truncate text-xs text-mist-500">{description}</span> : null}
        </span>
      </button>
      {trailing}
    </div>
  );
}
