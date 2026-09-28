import { cx } from "@/shared/lib/class-names";
import type { ComponentPropsWithoutRef } from "react";

export interface GlassSelectProps extends Omit<ComponentPropsWithoutRef<"select">, "className"> {
  readonly invalid?: boolean;
  readonly className?: string;
}

export function GlassSelect({ invalid = false, className, children, ...rest }: GlassSelectProps) {
  return (
    <select
      {...rest}
      aria-invalid={invalid}
      className={cx(
        "h-11 w-full appearance-none rounded-2xl border bg-ink-800/80 px-4 text-sm text-mist-100",
        "transition-colors duration-200 focus:border-aqua-400/60",
        invalid ? "border-rose-400/70" : "border-white/12 hover:border-white/20",
        className,
      )}
    >
      {children}
    </select>
  );
}
