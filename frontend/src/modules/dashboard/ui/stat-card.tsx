import { formatter } from "@/shared/lib/format";
import { MOTION_SPRING, staggerDelay } from "@/shared/lib/motion";
import { m } from "framer-motion";
import type { ReactNode } from "react";

export interface StatCardProps {
  readonly label: string;
  readonly value: number;
  readonly hint: string;
  readonly icon: ReactNode;
  readonly index: number;
}

export function StatCard({ label, value, hint, icon, index }: StatCardProps) {
  return (
    <m.article
      initial={{ opacity: 0, y: 14 }}
      animate={{ opacity: 1, y: 0 }}
      transition={staggerDelay(index)}
      whileHover={{ y: -3 }}
      className="glass glass-sheen rounded-3xl p-5"
    >
      <div className="flex items-start justify-between gap-3">
        <p className="text-xs font-medium tracking-wide text-mist-400 uppercase">{label}</p>
        <span className="text-aqua-300">{icon}</span>
      </div>
      <m.p
        className="mt-3 text-3xl font-semibold text-mist-100 tabular-nums"
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        transition={MOTION_SPRING}
      >
        {formatter.count(value)}
      </m.p>
      <p className="mt-1 text-xs text-mist-500">{hint}</p>
    </m.article>
  );
}
