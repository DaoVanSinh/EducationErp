import type { NamedReference } from "@/shared/api/schemas";
import { cx } from "@/shared/lib/class-names";
import { Badge } from "@/shared/ui/badge";
import { m } from "framer-motion";
import { Key, KeyRound } from "lucide";
import { ChevronRight } from "lucide-react";
import { MorphIcon } from "morphicons/react";
import { useState } from "react";

export interface GroupItemCardProps {
  readonly group: NamedReference;
  readonly isSelected?: boolean;
  readonly onSelect?: (id: string) => void;
}

export function GroupItemCard({ group, isSelected = false, onSelect }: GroupItemCardProps) {
  const [isHovered, setIsHovered] = useState(false);

  const handleClick = () => {
    if (onSelect) {
      onSelect(group.id);
    }
  };

  const isActive = isSelected || isHovered;

  return (
    <m.div
      whileHover={{ y: -2 }}
      whileTap={{ scale: 0.99 }}
      onClick={handleClick}
      onMouseEnter={() => setIsHovered(true)}
      onMouseLeave={() => setIsHovered(false)}
      role={onSelect ? "button" : undefined}
      tabIndex={onSelect ? 0 : undefined}
      onKeyDown={(e) => {
        if (onSelect && (e.key === "Enter" || e.key === " ")) {
          onSelect(group.id);
        }
      }}
      className={cx(
        "group relative flex items-center justify-between gap-4 rounded-2xl border p-4 transition-all duration-200",
        isSelected
          ? "border-amber-400 bg-white/95 shadow-md shadow-amber-500/15 ring-2 ring-amber-300/40"
          : "glass-deep glass-sheen border-white/90 hover:border-amber-300/80 hover:bg-white/80 hover:shadow-md",
        onSelect ? "cursor-pointer" : "",
      )}
    >
      <div className="flex min-w-0 items-center gap-3.5">
        <div
          className={cx(
            "flex size-11 shrink-0 items-center justify-center rounded-xl transition-all duration-200 shadow-xs",
            isSelected
              ? "bg-gradient-to-br from-amber-400 to-orange-500 text-white shadow-amber-500/30"
              : "border border-amber-100/80 bg-amber-50/70 text-amber-600 group-hover:bg-amber-500 group-hover:text-white",
          )}
        >
          <MorphIcon
            icon={isActive ? KeyRound : Key}
            spring="snappy"
            reducedMotion="user"
            size={20}
            strokeWidth={isActive ? 2.2 : 1.8}
            label={group.name}
          />
        </div>
        <div className="min-w-0">
          <h4 className="truncate text-sm font-bold text-slate-900">{group.name}</h4>
          <p className="mt-0.5 truncate text-xs text-slate-500 font-mono">
            ID: {group.id.slice(0, 8)}
          </p>
        </div>
      </div>

      <div className="flex items-center gap-2 shrink-0">
        <Badge tone="neutral">Nhóm quyền</Badge>
        {onSelect ? (
          <ChevronRight
            size={16}
            className="text-slate-400 transition-colors group-hover:text-amber-500"
            aria-hidden
          />
        ) : null}
      </div>
    </m.div>
  );
}
