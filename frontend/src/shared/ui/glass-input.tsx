import { cx } from "@/shared/lib/class-names";
import type { ComponentPropsWithoutRef } from "react";

export interface GlassInputProps extends Omit<ComponentPropsWithoutRef<"input">, "className"> {
  readonly invalid?: boolean;
  readonly className?: string;
}

export function GlassInput({ invalid = false, className, ...rest }: GlassInputProps) {
  return (
    <input
      {...rest}
      aria-invalid={invalid}
      className={cx(
        "h-11 w-full rounded-2xl border bg-white/75 px-4 text-sm text-slate-800 placeholder:text-slate-400",
        "backdrop-blur-md transition-all duration-200 focus:bg-white focus:outline-none focus:ring-2 focus:ring-orange-300/30",
        invalid
          ? "border-rose-400 focus:border-rose-500 focus:ring-rose-300/30"
          : "border-slate-200/90 hover:border-orange-300/60 focus:border-orange-400",
        className,
      )}
    />
  );
}
