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
        "h-11 w-full rounded-2xl border bg-white/5 px-4 text-sm text-mist-100 placeholder:text-mist-500",
        "backdrop-blur-sm transition-colors duration-200 focus:bg-white/10",
        invalid ? "border-rose-400/70" : "border-white/12 hover:border-white/20",
        className,
      )}
    />
  );
}
