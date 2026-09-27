import { m } from "framer-motion";

export function Spinner({ size = 20 }: { readonly size?: number }) {
  return (
    <m.span
      role="status"
      aria-label="Đang tải"
      style={{ width: size, height: size }}
      className="inline-block rounded-full border-2 border-white/20 border-t-aqua-400"
      animate={{ rotate: 360 }}
      transition={{ repeat: Number.POSITIVE_INFINITY, duration: 0.8, ease: "linear" }}
    />
  );
}
