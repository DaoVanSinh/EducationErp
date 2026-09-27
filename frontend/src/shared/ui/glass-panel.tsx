import { cx } from "@/shared/lib/class-names";
import { MOTION_SPRING, RISE_IN } from "@/shared/lib/motion";
import { m } from "framer-motion";
import type { ReactNode } from "react";

export interface GlassPanelProps {
  readonly children: ReactNode;
  readonly className?: string;
  /** Bề mặt nổi lên trên một panel khác (modal, dropdown) cần lớp kính đặc hơn để chữ còn đọc được. */
  readonly raised?: boolean;
  readonly animate?: boolean;
  readonly padded?: boolean;
}

export function GlassPanel({ children, className, raised = false, animate = true, padded = true }: GlassPanelProps) {
  const classes = cx(
    raised ? "glass-raised" : "glass",
    "rounded-3xl",
    padded && "p-6",
    className,
  );

  if (!animate) {
    return <div className={classes}>{children}</div>;
  }

  return (
    <m.div variants={RISE_IN} initial="hidden" animate="visible" transition={MOTION_SPRING} className={classes}>
      {children}
    </m.div>
  );
}
