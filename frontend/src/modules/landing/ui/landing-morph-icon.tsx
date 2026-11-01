import type { LandingMorphVariant } from "@/modules/landing/model/landing-schema";
import { cx } from "@/shared/lib/class-names";
import {
  Award,
  Clock,
  GraduationCap,
  ShieldCheck,
  Sparkles,
  Wallet,
  type LucideIcon,
} from "lucide-react";

const ICON_MAP: Record<string, LucideIcon> = {
  shield: ShieldCheck,
  sparkles: Sparkles,
  graduation: GraduationCap,
  wallet: Wallet,
  clock: Clock,
  award: Award,
};

export interface LandingMorphIconProps {
  readonly icon?: LucideIcon;
  readonly iconKey?: string;
  readonly variant?: LandingMorphVariant;
  readonly size?: number;
  readonly className?: string;
}

export function LandingMorphIcon({
  icon,
  iconKey,
  variant = "blue",
  size = 24,
  className,
}: LandingMorphIconProps) {
  const IconComponent = icon ?? (iconKey ? ICON_MAP[iconKey] : undefined) ?? Sparkles;

  return (
    <div className={cx("morphicon-wrap", className)}>
      <div className={cx("morphicon-shape", `variant-${variant}`)} />
      <div className="morphicon-icon">
        <IconComponent size={size} strokeWidth={2.2} aria-hidden />
      </div>
    </div>
  );
}
