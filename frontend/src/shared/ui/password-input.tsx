import { GlassInput, type GlassInputProps } from "@/shared/ui/glass-input";
import { MorphToggleIcon } from "@/shared/ui/morph-toggle-icon";
import { Eye, EyeOff } from "lucide";
import { useState } from "react";

/**
 * Ô mật khẩu có nút hiện/ẩn. Icon con mắt biến hình giữa hai trạng thái thay vì đổi hẳn sang một hình
 * khác, nên mắt người dùng không phải tìm lại nút sau mỗi lần bấm.
 */
export function PasswordInput({ invalid, ...rest }: Omit<GlassInputProps, "type">) {
  const [revealed, setRevealed] = useState(false);

  return (
    <div className="relative">
      <GlassInput {...rest} invalid={invalid} type={revealed ? "text" : "password"} className="pr-12" />
      <button
        type="button"
        onClick={() => setRevealed((previous) => !previous)}
        aria-label={revealed ? "Ẩn mật khẩu" : "Hiện mật khẩu"}
        className="absolute inset-y-0 right-0 flex w-12 items-center justify-center text-mist-400 transition-colors hover:text-mist-100"
      >
        <MorphToggleIcon
          inactive={Eye}
          active={EyeOff}
          isActive={revealed}
          label={revealed ? "Ẩn mật khẩu" : "Hiện mật khẩu"}
          size={18}
        />
      </button>
    </div>
  );
}
