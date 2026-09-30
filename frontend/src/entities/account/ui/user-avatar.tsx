import { cx } from "@/shared/lib/class-names";
import { formatter } from "@/shared/lib/format";

export interface UserAvatarProps {
  readonly fullName: string;
  readonly avatarUrl?: string | null;
  readonly size?: "sm" | "md" | "lg";
}

const SIZE_CLASS = {
  sm: "size-8 text-xs",
  md: "size-10 text-sm",
  lg: "size-14 text-base",
} as const;

export function UserAvatar({ fullName, avatarUrl, size = "md" }: UserAvatarProps) {
  const classes = cx(
    "inline-flex shrink-0 items-center justify-center rounded-full border border-white/80 font-semibold shadow-xs",
    SIZE_CLASS[size],
  );

  if (avatarUrl !== null && avatarUrl !== undefined && avatarUrl.length > 0) {
    return <img src={avatarUrl} alt={fullName} className={cx(classes, "object-cover")} />;
  }

  return (
    <span
      className={cx(
        classes,
        "bg-gradient-to-br from-[#FF8C42] to-[#FF5E62] text-white shadow-xs shadow-orange-500/20",
      )}
      aria-hidden
    >
      {formatter.initials(fullName)}
    </span>
  );
}
