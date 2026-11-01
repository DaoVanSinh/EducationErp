import {
  LANDING_SECTION_ID,
  LANDING_TAB,
  type LandingStats,
  type LandingTabId,
} from "@/modules/landing/model/landing-schema";

export interface LandingOverviewCardsProps {
  readonly stats: LandingStats;
  readonly onSelectTab: (tab: LandingTabId) => void;
}

export function LandingOverviewCards({ stats, onSelectTab }: LandingOverviewCardsProps) {
  return (
    <section id={LANDING_SECTION_ID.overview} className="bg-white pt-9 pb-3">
      <div className="mx-auto max-w-[1200px] px-4 sm:px-6">
        <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
          <button
            type="button"
            onClick={() => onSelectTab(LANDING_TAB.courses)}
            className="card-3d-hover cursor-pointer rounded-2xl border border-[#fed7aa] bg-gradient-to-b from-[#fff7ed] to-white p-5 text-left shadow-[0_4px_14px_rgba(255,140,66,0.06)]"
          >
            <span className="text-[11px] font-extrabold tracking-wide text-[#ea580c] uppercase">
              Phần 1: Chương Trình Đào Tạo Live
            </span>
            <h3 className="mt-1.5 text-[17px] font-extrabold text-[#1e293b]">
              {stats.activeCourseCount} Khoá Học & {stats.activeClassCount} Lớp Đang Mở →
            </h3>
            <p className="mt-1 text-[13px] text-[#64748b]">
              Tra cứu học phí niêm yết, cấu trúc cột điểm đánh giá và thời khoá biểu từng lớp
            </p>
          </button>

          <button
            type="button"
            onClick={() => onSelectTab(LANDING_TAB.teachers)}
            className="card-3d-hover cursor-pointer rounded-2xl border border-[#fecdd3] bg-gradient-to-b from-[#fff1f2] to-white p-5 text-left shadow-[0_4px_14px_rgba(244,63,94,0.05)]"
          >
            <span className="text-[11px] font-extrabold tracking-wide text-[#f43f5e] uppercase">
              Phần 2: Đội Ngũ Chuyên Môn
            </span>
            <h3 className="mt-1.5 text-[17px] font-extrabold text-[#1e293b]">
              {stats.activeTeacherCount} Giảng Viên Cơ Hữu & Thỉnh Giảng →
            </h3>
            <p className="mt-1 text-[13px] text-[#64748b]">
              Hồ sơ chuyên môn, môn giảng dạy phụ trách và tiểu sử học thuật của đội ngũ giảng viên
            </p>
          </button>

          <button
            type="button"
            onClick={() => onSelectTab(LANDING_TAB.branches)}
            className="card-3d-hover cursor-pointer rounded-2xl border border-[#bbf7d0] bg-gradient-to-b from-[#f0fdf4] to-white p-5 text-left shadow-[0_4px_14px_rgba(16,185,129,0.05)]"
          >
            <span className="text-[11px] font-extrabold tracking-wide text-[#059669] uppercase">
              Phần 3: Mạng Lưới & Ưu Đãi Học Phí
            </span>
            <h3 className="mt-1.5 text-[17px] font-extrabold text-[#1e293b]">
              {stats.activeBranchCount} Chi Nhánh & Ưu Đãi Combo -{stats.maxComboDiscountPercent}% →
            </h3>
            <p className="mt-1 text-[13px] text-[#64748b]">
              Hệ thống cơ sở đào tạo tiêu chuẩn và chính sách chiết khấu học phí tự động
            </p>
          </button>
        </div>
      </div>
    </section>
  );
}
