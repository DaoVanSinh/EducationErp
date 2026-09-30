import type { NamedReference } from "@/shared/api/schemas";
import { cx } from "@/shared/lib/class-names";
import { m } from "framer-motion";
import { Shield, ShieldCheck } from "lucide";
import { ChevronRight } from "lucide-react";
import { MorphIcon } from "morphicons/react";

export interface RoleItemCardProps {
  readonly role: NamedReference;
  readonly isSelected: boolean;
  readonly onSelect: (id: string) => void;
}

export function RoleItemCard({ role, isSelected, onSelect }: RoleItemCardProps) {
  return (
    <m.div
      whileHover={{ y: -2 }}
      whileTap={{ scale: 0.99 }}
      onClick={() => onSelect(role.id)}
      role="button"
      tabIndex={0}
      onKeyDown={(e) => {
        if (e.key === "Enter" || e.key === " ") onSelect(role.id);
      }}
      className={cx(
        "group relative flex cursor-pointer items-center justify-between gap-4 rounded-2xl border p-3.5 transition-all duration-200",
        isSelected
          ? "border-orange-400 bg-white/95 shadow-md shadow-orange-500/15 ring-2 ring-orange-300/40"
          : "glass-deep glass-sheen border-white/90 hover:border-orange-200/90 hover:bg-white/80 hover:shadow-md",
      )}
    >
      <div className="flex min-w-0 flex-1 items-center gap-3">
        <div
          className={cx(
            "flex size-11 shrink-0 items-center justify-center rounded-xl transition-all duration-200 shadow-xs",
            isSelected
              ? "bg-gradient-to-br from-[#FF8C42] to-[#FF5E62] text-white shadow-orange-500/30"
              : "border border-orange-100/80 bg-orange-50/70 text-orange-500 group-hover:bg-orange-500 group-hover:text-white",
          )}
        >
          <MorphIcon
            icon={isSelected ? ShieldCheck : Shield}
            spring="snappy"
            reducedMotion="user"
            size={20}
            strokeWidth={isSelected ? 2.2 : 1.8}
            label={role.name}
          />
        </div>
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-2">
            <h4 className="truncate text-sm font-bold text-slate-900">{role.name}</h4>
          </div>
          <p className="mt-0.5 truncate text-xs text-slate-500">
            ID: <span className="font-mono text-slate-600">{role.id.slice(0, 8)}</span>
          </p>
        </div>
      </div>

      <div
        className={cx(
          "flex size-7 items-center justify-center rounded-lg transition-colors shrink-0",
          isSelected ? "bg-orange-500/15 text-orange-600" : "text-slate-400 group-hover:text-orange-500",
        )}
      >
        <ChevronRight size={16} aria-hidden />
      </div>
    </m.div>
  );
}
