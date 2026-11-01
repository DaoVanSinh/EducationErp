import type { ConsultationFormErrors } from "@/modules/landing/hooks/use-consultation-form";
import {
  LANDING_SECTION_ID,
  type ConsultationFormData,
  type LandingBranch,
  type LandingCenterInfo,
  type LandingCourse,
} from "@/modules/landing/model/landing-schema";
import { CheckCircle2, Clock, Mail, MapPin, PhoneCall, Send, Sparkles } from "lucide-react";
import type { ChangeEvent, FormEvent } from "react";

interface LandingConsultationCtaProps {
  readonly centerInfo: LandingCenterInfo;
  readonly courses: readonly LandingCourse[];
  readonly branches: readonly LandingBranch[];
  readonly formData: ConsultationFormData;
  readonly errors: ConsultationFormErrors;
  readonly isSubmitting: boolean;
  readonly onChange: (
    event: ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>,
  ) => void;
  readonly onSubmit: (event: FormEvent<HTMLFormElement>) => void;
}

const INPUT_CLASS =
  "w-full rounded-xl border border-[#fed7aa] bg-[#fffaf5] px-4 py-3 text-[13.5px] font-medium text-[#1e293b] focus:border-[#ff8c42] focus:bg-white focus:outline-none";
const LABEL_CLASS = "mb-1.5 block text-[12.5px] font-bold text-[#475569]";

export function LandingConsultationCta({
  centerInfo,
  courses,
  branches,
  formData,
  errors,
  isSubmitting,
  onChange,
  onSubmit,
}: LandingConsultationCtaProps) {
  return (
    <section
      id={LANDING_SECTION_ID.consultation}
      className="relative overflow-hidden border-t border-[#ffedd5] bg-gradient-to-br from-[#fff7ed] via-[#fffaf5] to-[#fff1f2] py-20 text-[#1e293b] lg:py-24"
    >
      <div className="mx-auto max-w-[1280px] px-4 sm:px-6 lg:px-8">
        <div className="grid grid-cols-1 items-center gap-12 lg:grid-cols-12">
          <div className="lg:col-span-5">
            <span className="inline-flex items-center gap-1.5 rounded-full border border-[#fed7aa] bg-white px-4 py-1.5 text-[11px] font-bold tracking-wider text-[#ea580c] uppercase shadow-2xs">
              <Sparkles className="h-3.5 w-3.5" />
              ĐĂNG KÝ TƯ VẤN LỘ TRÌNH 1:1
            </span>
            <h2 className="mt-4 text-[30px] font-black leading-tight tracking-tight text-[#1e293b] sm:text-[38px]">
              Nhận tư vấn lộ trình & <span className="text-[#ea580c]">Giữ ưu đãi học bổng Combo</span>
            </h2>
            <p className="mt-4 text-[14.5px] leading-relaxed text-[#475569]">
              Để lại thông tin để bộ phận Tuyển sinh của {centerInfo.brandName} liên hệ tư vấn lộ trình học phù hợp và xếp lịch kiểm tra trình độ đầu vào miễn phí.
            </p>

            <div className="mt-8 space-y-3.5 rounded-2xl border border-[#fed7aa] bg-white/85 p-5 text-[13.5px] text-[#334155] shadow-xs">
              {centerInfo.hotlineDisplay && (
                <div className="flex items-center gap-3">
                  <PhoneCall className="h-4 w-4 shrink-0 text-[#ea580c]" />
                  <span>Hotline tư vấn: {centerInfo.hotlineDisplay}</span>
                </div>
              )}
              {centerInfo.email && (
                <div className="flex items-center gap-3">
                  <Mail className="h-4 w-4 shrink-0 text-[#ff5e62]" />
                  <span>Email: {centerInfo.email}</span>
                </div>
              )}
              {centerInfo.workTime && (
                <div className="flex items-center gap-3">
                  <Clock className="h-4 w-4 shrink-0 text-emerald-600" />
                  <span>Giờ làm việc: {centerInfo.workTime}</span>
                </div>
              )}
              {centerInfo.defaultAddress && (
                <div className="flex items-start gap-3">
                  <MapPin className="mt-0.5 h-4 w-4 shrink-0 text-amber-500" />
                  <span>{centerInfo.defaultAddress}</span>
                </div>
              )}
            </div>
          </div>

          <div className="rounded-[28px] border border-[#fed7aa] bg-white p-6 text-[#1e293b] shadow-[0_24px_64px_rgba(255,140,66,0.14)] sm:p-8 lg:col-span-7">
            <h3 className="text-[20px] font-black text-[#1e293b]">Phiếu đăng ký tư vấn & Kiểm tra đầu vào</h3>
            <p className="mt-1 text-[13px] text-[#64748b]">
              Điền thông tin bên dưới để nhận lịch khai giảng chi tiết và ưu đãi học phí mới nhất.
            </p>

            <form onSubmit={onSubmit} className="mt-6 space-y-4" noValidate>
              <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                <div>
                  <label htmlFor="consult-fullName" className={LABEL_CLASS}>Họ và tên học viên *</label>
                  <input id="consult-fullName" name="fullName" type="text" value={formData.fullName} onChange={onChange} placeholder="Nguyễn Văn A" className={INPUT_CLASS} />
                  {errors.fullName && <p className="mt-1 text-[12px] font-semibold text-[#f04452]">{errors.fullName}</p>}
                </div>
                <div>
                  <label htmlFor="consult-phone" className={LABEL_CLASS}>Số điện thoại / Zalo *</label>
                  <input id="consult-phone" name="phone" type="tel" value={formData.phone} onChange={onChange} placeholder="0901 234 567" className={INPUT_CLASS} />
                  {errors.phone && <p className="mt-1 text-[12px] font-semibold text-[#f04452]">{errors.phone}</p>}
                </div>
              </div>

              <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
                <div>
                  <label htmlFor="consult-email" className={LABEL_CLASS}>Email</label>
                  <input id="consult-email" name="email" type="email" value={formData.email} onChange={onChange} placeholder="hocvien@email.com" className={INPUT_CLASS} />
                </div>
                <div>
                  <label htmlFor="consult-course" className={LABEL_CLASS}>Khoá học quan tâm</label>
                  <select id="consult-course" name="targetCourseId" value={formData.targetCourseId} onChange={onChange} className={INPUT_CLASS}>
                    <option value="">-- Chọn khoá học --</option>
                    {courses.map((c) => (
                      <option key={c.id} value={c.id}>{c.code} - {c.name}</option>
                    ))}
                  </select>
                </div>
                <div>
                  <label htmlFor="consult-branch" className={LABEL_CLASS}>Cơ sở mong muốn</label>
                  <select id="consult-branch" name="targetBranchId" value={formData.targetBranchId} onChange={onChange} className={INPUT_CLASS}>
                    <option value="">-- Chọn cơ sở học --</option>
                    {branches.map((b) => (
                      <option key={b.id} value={b.id}>{b.name}</option>
                    ))}
                  </select>
                </div>
              </div>

              <div>
                <label htmlFor="consult-notes" className={LABEL_CLASS}>Mục tiêu học tập / Khung giờ rảnh</label>
                <textarea id="consult-notes" name="notes" rows={3} value={formData.notes} onChange={onChange} placeholder="Ví dụ: Mình muốn học buổi tối 2-4-6, cần tư vấn lộ trình từ cơ bản..." className={INPUT_CLASS} />
              </div>

              {errors.submit && (
                <p className="rounded-xl bg-[#fff0f3] px-4 py-2.5 text-[12.5px] font-semibold text-[#f04452]">{errors.submit}</p>
              )}

              <button
                type="submit"
                disabled={isSubmitting}
                className="inline-flex w-full cursor-pointer items-center justify-center gap-2 rounded-full bg-gradient-to-r from-[#ff8c42] to-[#ff5e62] px-6 py-4 text-[14.5px] font-bold text-white shadow-[0_10px_28px_rgba(255,140,66,0.32)] transition-all hover:opacity-95 disabled:opacity-60"
              >
                <Send className="h-4 w-4" />
                {isSubmitting ? "Đang gửi đăng ký..." : "Gửi đăng ký tư vấn ngay"}
              </button>

              <p className="flex items-center justify-center gap-1.5 text-center text-[12px] text-[#64748b]">
                <CheckCircle2 className="h-3.5 w-3.5 text-emerald-500" />
                Bảo mật thông tin 100% • Tư vấn viên liên hệ trong 15–30 phút
              </p>
            </form>
          </div>
        </div>
      </div>
    </section>
  );
}
