import { cx } from "@/shared/lib/class-names";
import { Spinner } from "@/shared/ui/spinner";
import { m } from "framer-motion";
import type { ComponentPropsWithoutRef, ReactNode } from "react";

const VARIANT_CLASS = {
  primary:
    "bg-gradient-to-br from-violet-500/90 to-aqua-500/80 text-mist-100 border-white/20 hover:from-violet-400 hover:to-aqua-400",
  secondary: "glass text-mist-100 hover:border-white/25",
  ghost: "border-transparent text-mist-300 hover:text-mist-100 hover:bg-white/5",
  danger: "bg-rose-400/20 text-rose-400 border-rose-400/40 hover:bg-rose-400/30",
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
        "transition-colors duration-200 disabled:cursor-not-allowed disabled:opacity-50",
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
