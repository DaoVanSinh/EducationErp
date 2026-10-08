import { cx } from "@/shared/lib/class-names";
import { Spinner } from "@/shared/ui/spinner";
import { m } from "framer-motion";
import type { ComponentPropsWithoutRef, ReactNode } from "react";

const VARIANT_CLASS = {
  primary:
    "bg-gradient-to-r from-[#FF8C42] via-[#FF755A] to-[#FF5E62] text-white border-transparent hover:from-[#FF7828] hover:to-[#FF4A4E] shadow-sm shadow-orange-500/25 active:shadow-none",
  secondary: "glass text-slate-800 border-white/80 hover:bg-white/95 hover:border-orange-300/50 shadow-xs",
  ghost: "border-transparent text-slate-600 hover:text-orange-600 hover:bg-orange-50/60",
  danger: "bg-rose-50 text-rose-600 border-rose-200/80 hover:bg-rose-100/80",
} as const;

const SIZE_CLASS = {
  sm: "h-9 px-3 text-sm",
  md: "h-11 px-5 text-sm",
} as const;

/**
 * Các handler mà DOM và framer-motion trùng tên nhưng khác chữ ký (onAnimationStart nhận AnimationEvent
 * của DOM, còn của motion nhận definition). Bỏ chúng khỏi props để kiểu không xung đột khi spread vào m.button.
 */
type ButtonPropsWithoutMotionClash = Omit<
  ComponentPropsWithoutRef<"button">,
  "className" | "onAnimationStart" | "onAnimationEnd" | "onAnimationIteration" | "onDrag" | "onDragStart" | "onDragEnd" | "style"
>;

export interface GlassButtonProps extends ButtonPropsWithoutMotionClash {
  readonly variant?: keyof typeof VARIANT_CLASS;
  readonly size?: keyof typeof SIZE_CLASS;
  readonly loading?: boolean;
  readonly icon?: ReactNode;
  readonly className?: string;
}

export function GlassButton({
  variant = "primary",
  size = "md",
  loading = false,
  icon,
  className,
  children,
  disabled,
  type = "button",
  ...rest
}: GlassButtonProps) {
  return (
    <m.button
      {...rest}
      type={type}
      disabled={disabled === true || loading}
      whileHover={disabled === true || loading ? undefined : { y: -1 }}
      whileTap={disabled === true || loading ? undefined : { scale: 0.98 }}
      className={cx(
        "inline-flex items-center justify-center gap-2 rounded-2xl border font-medium",
        "transition-all duration-200 disabled:cursor-not-allowed disabled:opacity-50",
        VARIANT_CLASS[variant],
        SIZE_CLASS[size],
        className,
      )}
    >
      {loading ? <Spinner size={16} /> : icon}
      {children}
    </m.button>
  );
}
