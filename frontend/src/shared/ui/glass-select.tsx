import { cx } from "@/shared/lib/class-names";
import { ChevronDown } from "lucide-react";
import type { ComponentPropsWithoutRef } from "react";

export interface GlassSelectProps extends Omit<ComponentPropsWithoutRef<"select">, "className"> {
  readonly invalid?: boolean;
  readonly className?: string;
  readonly containerClassName?: string;
}

export function GlassSelect({
  invalid = false,
  className,
  containerClassName,
  children,
  ...rest
}: GlassSelectProps) {
  return (
    <div className={cx("relative inline-flex items-center", containerClassName)}>
      <select
        {...rest}
        aria-invalid={invalid}
        className={cx(
          "h-10 w-full appearance-none rounded-xl border bg-white/95 pl-3 pr-8 text-xs font-semibold text-slate-800 shadow-2xs backdrop-blur-md",
          "transition-all duration-200 focus:bg-white focus:outline-none focus:ring-2 focus:ring-orange-400/30 focus:border-orange-500",
          invalid
            ? "border-rose-400 focus:border-rose-500 focus:ring-rose-300/30"
            : "border-slate-200/90 hover:border-orange-300/80",
          className,
        )}
      >
        {children}
      </select>
      <ChevronDown
        size={14}
        className="pointer-events-none absolute right-2.5 top-1/2 -translate-y-1/2 text-slate-400"
        aria-hidden
      />
    </div>
  );
}
