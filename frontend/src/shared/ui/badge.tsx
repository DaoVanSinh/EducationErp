import { cx } from "@/shared/lib/class-names";
import type { ReactNode } from "react";

const TONE_CLASS = {
  neutral: "bg-white/8 text-mist-300 border-white/12",
  positive: "bg-aqua-500/15 text-aqua-300 border-aqua-400/30",
  warning: "bg-amber-300/15 text-amber-300 border-amber-300/30",
  danger: "bg-rose-400/15 text-rose-400 border-rose-400/30",
  accent: "bg-violet-500/15 text-violet-400 border-violet-400/30",
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
