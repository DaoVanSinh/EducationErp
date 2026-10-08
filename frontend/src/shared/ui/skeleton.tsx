import { cx } from "@/shared/lib/class-names";

/** Khối giữ chỗ trong lúc tải, để khung màn hình không nhảy khi dữ liệu về. */
export function Skeleton({ className }: { readonly className?: string }) {
  return <div className={cx("animate-pulse rounded-2xl bg-slate-200/70", className)} />;
}
