import { AnimatePresence, m } from "framer-motion";
import { CheckCircle2, Sparkles, X } from "lucide-react";

interface LandingConsultationModalProps {
  readonly isOpen: boolean;
  readonly brandName: string;
  readonly hotlineDisplay: string;
  readonly onClose: () => void;
}

export function LandingConsultationModal({
  isOpen,
  brandName,
  hotlineDisplay,
  onClose,
}: LandingConsultationModalProps) {
  return (
    <AnimatePresence>
      {isOpen && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-[#431407]/25 p-4 backdrop-blur-sm"
          onClick={onClose}
          role="dialog"
          aria-modal="true"
          aria-labelledby="consultation-success-title"
        >
          <m.div
            initial={{ opacity: 0, scale: 0.92, y: 16 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: 0.92, y: 16 }}
            transition={{ duration: 0.25 }}
            onClick={(e) => e.stopPropagation()}
            className="relative w-full max-w-[460px] rounded-[28px] border border-[#fed7aa] bg-white p-8 text-center shadow-[0_24px_64px_rgba(255,140,66,0.22)]"
          >
            <button
              type="button"
              onClick={onClose}
              aria-label="Đóng thông báo"
              className="absolute top-4 right-4 flex h-9 w-9 cursor-pointer items-center justify-center rounded-full bg-[#fff7ed] text-[#475569] hover:bg-[#ffedd5]"
            >
              <X className="h-4 w-4" />
            </button>

            <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-2xl bg-emerald-50 text-emerald-600 shadow-xs">
              <CheckCircle2 className="h-9 w-9" />
            </div>

            <span className="mt-4 inline-flex items-center gap-1 rounded-full border border-[#fed7aa] bg-[#fff7ed] px-3 py-1 text-[11px] font-bold text-[#ea580c] uppercase">
              <Sparkles className="h-3.5 w-3.5" />
              Đã tiếp nhận thông tin
            </span>

            <h3
              id="consultation-success-title"
              className="mt-3 text-[22px] font-black text-[#1e293b]"
            >
              Đăng ký tư vấn thành công!
            </h3>

            <p className="mt-2 text-[14px] leading-relaxed text-[#475569]">
              Bộ phận Tuyển sinh của <strong>{brandName}</strong> đã nhận được yêu cầu của bạn trên
              hệ thống và sẽ liên hệ lại trong vòng 15–30 phút.
            </p>

            {hotlineDisplay && (
              <div className="mt-5 rounded-2xl border border-[#ffedd5] bg-[#fffaf5] p-3.5 text-[13px] font-semibold text-[#475569]">
                Cần hỗ trợ gấp? Gọi ngay Hotline:{" "}
                <strong className="text-[#ea580c]">{hotlineDisplay}</strong>
              </div>
            )}

            <button
              type="button"
              onClick={onClose}
              className="mt-6 w-full cursor-pointer rounded-full bg-gradient-to-r from-[#ff8c42] to-[#ff5e62] py-3.5 text-[14px] font-bold text-white shadow-[0_8px_20px_rgba(255,140,66,0.28)] hover:opacity-95"
            >
              Hoàn tất
            </button>
          </m.div>
        </div>
      )}
    </AnimatePresence>
  );
}
