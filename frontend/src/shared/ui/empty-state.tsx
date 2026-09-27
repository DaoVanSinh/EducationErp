import type { ReactNode } from "react";

export interface EmptyStateProps {
  readonly title: string;
  readonly description?: string;
  readonly icon?: ReactNode;
  readonly action?: ReactNode;
}

export function EmptyState({ title, description, icon, action }: EmptyStateProps) {
  return (
    <div className="flex flex-col items-center gap-3 rounded-3xl border border-dashed border-white/12 px-6 py-12 text-center">
      {icon ? <span className="text-mist-400">{icon}</span> : null}
      <p className="text-sm font-medium text-mist-200">{title}</p>
      {description ? <p className="max-w-md text-sm text-mist-500">{description}</p> : null}
      {action}
    </div>
  );
}
