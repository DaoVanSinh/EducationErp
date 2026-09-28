import { MOTION_SPRING } from "@/shared/lib/motion";
import { m } from "framer-motion";
import type { ReactNode } from "react";

export interface PageHeaderProps {
  readonly title: string;
  readonly description?: string;
  readonly actions?: ReactNode;
}

export function PageHeader({ title, description, actions }: PageHeaderProps) {
  return (
    <m.header
      initial={{ opacity: 0, y: -8 }}
      animate={{ opacity: 1, y: 0 }}
      transition={MOTION_SPRING}
      className="flex flex-wrap items-end justify-between gap-4"
    >
      <div>
        <h1 className="text-2xl font-semibold tracking-tight text-mist-100">{title}</h1>
        {description ? <p className="mt-1 max-w-2xl text-sm text-mist-400">{description}</p> : null}
      </div>
      {actions ? <div className="flex items-center gap-2">{actions}</div> : null}
    </m.header>
  );
}
