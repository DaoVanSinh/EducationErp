import type { CourseFilterCategory } from "@/modules/landing/hooks/use-landing-catalog-filter";
import { LANDING_SECTION_ID, type LandingCourse } from "@/modules/landing/model/landing-schema";
import { LandingCourseCard } from "@/modules/landing/ui/landing-course-card";
import { LandingSectionHeader } from "@/modules/landing/ui/landing-section-header";
import { BookOpen, Search } from "lucide-react";
import type { ChangeEvent } from "react";

interface LandingCourseGridProps {
  readonly categories: readonly CourseFilterCategory[];
  readonly selectedCategory: string;
  readonly searchQuery: string;
  readonly filteredCourses: readonly LandingCourse[];
  readonly isLoading: boolean;
  readonly onSelectCategory: (categoryId: string) => void;
  readonly onSearchChange: (event: ChangeEvent<HTMLInputElement>) => void;
  readonly onOpenCourseDetail: (course: LandingCourse) => void;
}

export function LandingCourseGrid({
  categories,
  selectedCategory,
  searchQuery,
  filteredCourses,
  isLoading,
  onSelectCategory,
  onSearchChange,
  onOpenCourseDetail,
}: LandingCourseGridProps) {
  return (
    <section id={LANDING_SECTION_ID.courses} className="bg-[#fffaf5] py-20 lg:py-24">
      <div className="mx-auto max-w-[1280px] px-4 sm:px-6 lg:px-8">
        <LandingSectionHeader
          badge="KHOÁ HỌC & LỚP ĐANG TUYỂN SINH"
          title="Danh mục"
          highlight="Khoá học & Lịch khai giảng"
          subtitle="Khám phá chi tiết chương trình học, mức học phí niêm yết, tiêu chí đánh giá và lịch học các lớp đang tuyển sinh tại từng cơ sở."
        />

        <div className="mb-10 flex flex-col items-stretch justify-between gap-4 rounded-[24px] border border-[#ffedd5] bg-white p-4 shadow-[0_4px_20px_rgba(255,140,66,0.05)] lg:flex-row lg:items-center">
          <div className="flex flex-wrap items-center gap-2">
            {categories.map((cat) => {
              const active = selectedCategory === cat.id;
              return (
                <button
                  key={cat.id}
                  type="button"
                  onClick={() => onSelectCategory(cat.id)}
                  className={`cursor-pointer rounded-full px-4 py-2 text-[13px] font-bold transition-all ${
                    active
                      ? "bg-gradient-to-r from-[#ff8c42] to-[#ff5e62] text-white shadow-[0_6px_16px_rgba(255,140,66,0.28)]"
                      : "bg-[#fffaf5] text-[#475569] hover:bg-[#fff7ed] hover:text-[#ea580c]"
                  }`}
                >
                  {cat.label}
                </button>
              );
            })}
          </div>

          <div className="relative min-w-[260px] lg:w-[320px]">
            <Search className="pointer-events-none absolute top-1/2 left-3.5 h-4 w-4 -translate-y-1/2 text-[#94a3b8]" />
            <input
              type="text"
              value={searchQuery}
              onChange={onSearchChange}
              placeholder="Tìm mã khoá, tên khoá học..."
              aria-label="Tìm kiếm khoá học"
              className="w-full rounded-full border border-[#fed7aa] bg-[#fffaf5] py-2.5 pr-4 pl-10 text-[13.5px] font-medium text-[#1e293b] placeholder-[#94a3b8] transition-colors focus:border-[#ff8c42] focus:bg-white focus:outline-none"
            />
          </div>
        </div>

        {isLoading && (
          <div className="grid grid-cols-1 gap-6 md:grid-cols-2 lg:grid-cols-3">
            {[1, 2, 3].map((skeleton) => (
              <div
                key={skeleton}
                className="h-[340px] animate-pulse rounded-[24px] border border-[#ffedd5] bg-white p-6"
              />
            ))}
          </div>
        )}

        {!isLoading && filteredCourses.length === 0 && (
          <div className="rounded-[24px] border border-dashed border-[#fed7aa] bg-white px-6 py-16 text-center">
            <div className="mx-auto mb-3 flex h-12 w-12 items-center justify-center rounded-2xl bg-[#fff7ed] text-[#ea580c]">
              <BookOpen className="h-6 w-6" />
            </div>
            <p className="text-[16px] font-bold text-[#1e293b]">
              Không tìm thấy khoá học phù hợp với bộ lọc hiện tại
            </p>
            <p className="mt-1 text-[13.5px] text-[#64748b]">
              Hãy thử chọn danh mục &ldquo;Tất cả khoá học&rdquo; hoặc thay đổi từ khoá tìm kiếm.
            </p>
          </div>
        )}

        {!isLoading && filteredCourses.length > 0 && (
          <div className="grid grid-cols-1 gap-6 md:grid-cols-2 lg:grid-cols-3">
            {filteredCourses.map((course, index) => (
              <LandingCourseCard
                key={course.id}
                course={course}
                index={index}
                onOpenDetail={onOpenCourseDetail}
              />
            ))}
          </div>
        )}
      </div>
    </section>
  );
}
