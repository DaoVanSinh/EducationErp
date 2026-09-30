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
      className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between min-w-0 w-full"
    >
      <div className="min-w-0 flex-1">
        <h1 className="text-xl sm:text-2xl font-extrabold tracking-tight text-slate-900 break-words">
          {title}
        </h1>
        {description ? (
          <p className="mt-1 text-xs sm:text-sm text-slate-500 break-words leading-relaxed">
            {description}
          </p>
        ) : null}
      </div>
      {actions ? <div className="flex flex-wrap items-center gap-2 shrink-0">{actions}</div> : null}
    </m.header>
  );
}
