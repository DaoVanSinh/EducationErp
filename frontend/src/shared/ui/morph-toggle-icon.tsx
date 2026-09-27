import type { IconInput } from "morphicons/react";
import { MorphIcon } from "morphicons/react";

export interface MorphToggleIconProps {
  /** Dữ liệu icon lấy từ gói `lucide` (mảng path), không phải component của `lucide-react`. */
  readonly inactive: IconInput;
  readonly active: IconInput;
  readonly isActive: boolean;
  readonly label: string;
  readonly size?: number;
}

/**
 * Icon biến hình giữa hai trạng thái (menu ⇄ đóng, mắt mở ⇄ mắt nhắm). Dùng chế độ không điều khiển
 * của morphicons: đổi prop `icon` là nó tự chạy spring, nên không phải tự quản lý progress.
 */
export function MorphToggleIcon({ inactive, active, isActive, label, size = 20 }: MorphToggleIconProps) {
  return (
    <MorphIcon
      icon={isActive ? active : inactive}
      spring="snappy"
      reducedMotion="user"
      size={size}
      strokeWidth={1.75}
      label={label}
    />
  );
}
