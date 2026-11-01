import type { LandingCenterInfo } from "@/modules/landing/model/landing-schema";
import { Save } from "lucide-react";
import type { ChangeEvent, FormEvent } from "react";

interface AdminCenterInfoFormProps {
  readonly centerInfo: LandingCenterInfo;
  readonly isSaving: boolean;
  readonly saveMessage: string | null;
  readonly onChange: (event: ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => void;
  readonly onSubmit: (event: FormEvent<HTMLFormElement>) => void;
}

const TEXT_FIELDS: readonly {
  readonly name: keyof LandingCenterInfo;
  readonly label: string;
  readonly colSpan?: string;
}[] = [
  { name: "brandName", label: "Tên thương hiệu trung tâm" },
  { name: "brandSubtitle", label: "Khẩu hiệu phụ (Subtitle)" },
  { name: "tagline", label: "Thông điệp nổi bật (Tagline)" },
  { name: "heroBadge", label: "Nhãn nổi bật Hero (Badge)" },
  { name: "heroTitle", label: "Tiêu đề chính Hero" },
  { name: "heroHighlight", label: "Dòng nhấn mạnh Hero" },
  { name: "hotline", label: "Số Hotline (dạng số gọi)" },
  { name: "hotlineDisplay", label: "Số Hotline hiển thị" },
  { name: "email", label: "Email liên hệ tuyển sinh" },
  { name: "workTime", label: "Khung giờ làm việc" },
  { name: "zaloUrl", label: "Đường dẫn Zalo OA / Chat" },
  { name: "messengerUrl", label: "Đường dẫn Messenger" },
  { name: "defaultAddress", label: "Địa chỉ trụ sở chính", colSpan: "sm:col-span-2" },
];

export function AdminCenterInfoForm({
  centerInfo,
  isSaving,
  saveMessage,
  onChange,
  onSubmit,
}: AdminCenterInfoFormProps) {
  return (
    <form onSubmit={onSubmit} className="space-y-6 rounded-2xl border border-slate-200 bg-white p-6">
      <div>
        <h2 className="text-lg font-bold text-slate-900">
          Thông tin Trung tâm, Liên hệ & Giao diện Hero
        </h2>
        <p className="mt-1 text-sm text-slate-500">
          Mọi thay đổi tại đây sẽ cập nhật trực tiếp lên Trang chủ giới thiệu công khai (/).
        </p>
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        {TEXT_FIELDS.map((field) => (
          <div key={field.name} className={field.colSpan ?? ""}>
            <label
              htmlFor={`center-${field.name}`}
              className="mb-1 block text-xs font-semibold text-slate-700"
            >
              {field.label}
            </label>
            <input
              id={`center-${field.name}`}
              name={field.name}
              type="text"
              value={centerInfo[field.name]}
              onChange={onChange}
              required
              className="w-full rounded-xl border border-slate-300 px-3.5 py-2 text-sm text-slate-900 focus:border-orange-500 focus:outline-none"
            />
          </div>
        ))}

        <div className="sm:col-span-2">
          <label
            htmlFor="center-heroDescription"
            className="mb-1 block text-xs font-semibold text-slate-700"
          >
            Mô tả chi tiết phần mở đầu (Hero Description)
          </label>
          <textarea
            id="center-heroDescription"
            name="heroDescription"
            rows={3}
            value={centerInfo.heroDescription}
            onChange={onChange}
            required
            className="w-full rounded-xl border border-slate-300 px-3.5 py-2 text-sm text-slate-900 focus:border-orange-500 focus:outline-none"
          />
        </div>

        <div className="sm:col-span-2">
          <label
            htmlFor="center-footerDescription"
            className="mb-1 block text-xs font-semibold text-slate-700"
          >
            Mô tả chân trang (Footer Description)
          </label>
          <textarea
            id="center-footerDescription"
            name="footerDescription"
            rows={2}
            value={centerInfo.footerDescription}
            onChange={onChange}
            required
            className="w-full rounded-xl border border-slate-300 px-3.5 py-2 text-sm text-slate-900 focus:border-orange-500 focus:outline-none"
          />
        </div>
      </div>

      {saveMessage && (
        <div className="rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm font-medium text-emerald-700">
          {saveMessage}
        </div>
      )}

      <div className="flex justify-end">
        <button
          type="submit"
          disabled={isSaving}
          className="inline-flex cursor-pointer items-center gap-2 rounded-xl bg-gradient-to-r from-[#ff8c42] to-[#ff5e62] px-5 py-2.5 text-sm font-semibold text-white shadow-xs hover:opacity-95 disabled:opacity-60"
        >
          <Save className="h-4 w-4" />
          {isSaving ? "Đang lưu cấu hình..." : "Lưu thông tin trung tâm"}
        </button>
      </div>
    </form>
  );
}
