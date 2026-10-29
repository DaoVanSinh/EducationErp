import { ErrorNotice } from "@/shared/ui/error-notice";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassModal } from "@/shared/ui/glass-modal";
import type { ReactNode } from "react";

export interface ConfirmDialogProps {
  readonly open: boolean;
  readonly title: string;
  /** Hệ quả của thao tác, nói rõ để người dùng biết mình đang đồng ý với điều gì. */
  readonly description: ReactNode;
  readonly confirmLabel: string;
  /** `danger` cho thao tác khó hoàn tác (khoá tài khoản, huỷ lớp). */
  readonly tone?: "primary" | "danger";
  readonly isPending?: boolean;
  readonly error?: unknown;
  readonly onConfirm: () => void;
  readonly onClose: () => void;
}

/** Hỏi lại trước một thao tác quan trọng - thay cho window.confirm, giữ đúng giao diện và hiện được lỗi API. */
export function ConfirmDialog({
  open,
  title,
  description,
  confirmLabel,
  tone = "primary",
  isPending = false,
  error,
  onConfirm,
  onClose,
}: ConfirmDialogProps) {
  return (
    <GlassModal open={open} onClose={onClose} title={title}>
      <div className="flex flex-col gap-4">
        {error ? <ErrorNotice error={error} /> : null}
        <div className="text-sm leading-relaxed text-slate-600">{description}</div>
      </div>
      <footer className="flex justify-end gap-2">
        <GlassButton variant="ghost" onClick={onClose} disabled={isPending}>
          Huỷ bỏ
        </GlassButton>
        <GlassButton variant={tone} onClick={onConfirm} loading={isPending}>
          {confirmLabel}
        </GlassButton>
      </footer>
    </GlassModal>
  );
}
