import { cx } from "@/shared/lib/class-names";

export interface BrandLogoIconProps {
  readonly size?: "sm" | "md" | "lg" | "xl";
  readonly className?: string;
}

const ICON_SIZE_CLASS = {
  sm: "size-9 rounded-xl",
  md: "size-10 rounded-xl",
  lg: "size-12 rounded-2xl",
  xl: "size-16 rounded-2xl",
} as const;

/**
 * Biểu tượng nhận diện thương hiệu DS Academy sử dụng ảnh PNG hiện đại:
 * Chữ "DS" lớn kèm chữ "academy" nhỏ bên dưới trên nền Cam Pastel.
 */
export function BrandLogoIcon({ size = "md", className }: BrandLogoIconProps) {
  return (
    <span
      className={cx(
        "relative inline-flex shrink-0 items-center justify-center overflow-hidden select-none",
        "shadow-[0_8px_20px_-4px_rgba(249,115,22,0.35)] ring-1 ring-white/60",
        ICON_SIZE_CLASS[size],
        className,
      )}
    >
      <img
        src="/images/ds-academy-logo-v2.png"
        alt="DS Academy"
        className="h-full w-full object-cover"
      />
    </span>
  );
}
