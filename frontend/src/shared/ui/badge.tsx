import { cx } from "@/shared/lib/class-names";
import type { ReactNode } from "react";

const TONE_CLASS = {
  neutral: "bg-slate-100/90 text-slate-700 border-slate-200/90",
  positive: "bg-emerald-50 text-emerald-700 border-emerald-200/90",
  warning: "bg-amber-50 text-amber-700 border-amber-200/90",
  danger: "bg-rose-50 text-rose-600 border-rose-200/90",
  accent: "bg-orange-50 text-orange-600 border-orange-200/90",
} as const;

export interface BadgeProps {
  readonly children: ReactNode;
  readonly tone?: keyof typeof TONE_CLASS;
}

export function Badge({ children, tone = "neutral" }: BadgeProps) {
  return (
    <span
      className={cx(
        "inline-flex items-center gap-1 rounded-full border px-2.5 py-0.5 text-xs font-medium",
        TONE_CLASS[tone],
      )}
    >
      {children}
    </span>
  );
}
