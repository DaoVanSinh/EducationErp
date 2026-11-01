import type { ConsultPrefill } from "@/modules/landing/hooks/use-consultation-form";
import { sessionCountLabel } from "@/modules/landing/model/landing-class-rules";
import type { LandingClass, LandingCourse } from "@/modules/landing/model/landing-schema";
import { LandingCourseClassRow } from "@/modules/landing/ui/landing-course-class-row";
import { formatter } from "@/shared/lib/format";
import { AnimatePresence, m } from "framer-motion";
import { Award, BookOpen, Calendar, Clock, Sparkles, Users, X } from "lucide-react";

interface LandingCourseModalProps {
  readonly course: LandingCourse | null;
  readonly courseClasses: readonly LandingClass[];
  /** % giảm khi rủ thêm 1 người học chung (nhóm 2 người); 0 = chưa cấu hình ưu đãi. */
  readonly groupDiscountPercent: number;
  readonly onClose: () => void;
  readonly onConsultCourse: (options: ConsultPrefill) => void;
}

export function LandingCourseModal({
  course,
  courseClasses,
  groupDiscountPercent,
  onClose,
  onConsultCourse,
}: LandingCourseModalProps) {
  return (
    <AnimatePresence>
      {course !== null && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-[#431407]/25 p-4 backdrop-blur-sm"
          onClick={onClose}
          role="dialog"
          aria-modal="true"
          aria-labelledby="landing-course-modal-title"
        >
          <m.div
            initial={{ opacity: 0, scale: 0.95, y: 16 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: 0.95, y: 16 }}
            transition={{ duration: 0.25 }}
            onClick={(e) => e.stopPropagation()}
            className="max-h-[88vh] w-full max-w-[760px] overflow-y-auto rounded-[28px] border border-[#fed7aa] bg-white p-6 shadow-[0_24px_64px_rgba(255,140,66,0.2)] sm:p-8"
          >
            <div className="flex items-start justify-between gap-4 border-b border-[#ffedd5] pb-5">
              <div>
                <div className="mb-2 flex flex-wrap items-center gap-2">
                  <span className="inline-flex items-center gap-1.5 rounded-full border border-[#fed7aa] bg-[#fff7ed] px-3 py-1 text-[11px] font-bold text-[#ea580c] uppercase">
                    <BookOpen className="h-3.5 w-3.5" />
                    Mã khoá: {course.code}
                  </span>
                  <span className="inline-flex items-center gap-1 rounded-full bg-[#fff0f3] px-3 py-1 text-[11px] font-bold text-[#f04452]">
                    <Calendar className="h-3.5 w-3.5" />
                    {sessionCountLabel(course.standardSessionCount)}
                  </span>
                </div>
                <h3 id="landing-course-modal-title" className="text-[22px] font-black text-[#1e293b] sm:text-[26px]">
                  {course.name}
                </h3>
              </div>
              <button
                type="button"
                onClick={onClose}
                aria-label="Đóng chi tiết khoá học"
                className="flex h-10 w-10 shrink-0 cursor-pointer items-center justify-center rounded-full bg-[#fff7ed] text-[#475569] hover:bg-[#ffedd5]"
              >
                <X className="h-5 w-5" />
              </button>
            </div>

            <div className="mt-5 space-y-6">
              <div className="rounded-2xl border border-[#ffedd5] bg-[#fffaf5] p-4">
                <p className="text-[14px] leading-relaxed text-[#475569]">
                  {course.description ?? "Khoá học được thiết kế bài bản, theo sát tiến độ học tập và cam kết chuẩn đầu ra."}
                </p>
                <div className="mt-3 flex flex-wrap items-center justify-between gap-2 border-t border-[#ffedd5] pt-3">
                  <span className="text-[12.5px] font-semibold text-[#64748b]">Học phí trọn khoá:</span>
                  <span className="text-[20px] font-black text-[#ea580c]">
                    {course.tuitionFee !== null ? formatter.currency(course.tuitionFee) : "Liên hệ tư vấn"}
                  </span>
                </div>
                {groupDiscountPercent > 0 && course.tuitionFee !== null && (
                  <p className="mt-3 inline-flex items-center gap-1.5 rounded-xl bg-white px-3 py-2 text-[12.5px] font-semibold text-[#9a3412] ring-1 ring-[#fed7aa]">
                    <Users className="h-3.5 w-3.5 text-[#ea580c]" aria-hidden />
                    Rủ bạn học chung từ 2 người: mỗi người giảm {groupDiscountPercent}%, chỉ còn{" "}
                    {formatter.currency(Math.round(course.tuitionFee * (1 - groupDiscountPercent / 100)))}
                  </p>
                )}
              </div>

              {course.gradeColumns.length > 0 && (
                <div>
                  <h4 className="mb-3 flex items-center gap-2 text-[14px] font-extrabold text-[#1e293b] uppercase">
                    <Award className="h-4 w-4 text-[#ea580c]" />
                    Cấu trúc cột điểm đánh giá ({course.gradeColumns.length} cột)
                  </h4>
                  <div className="grid grid-cols-1 gap-2.5 sm:grid-cols-3">
                    {course.gradeColumns.map((col) => (
                      <div key={col.name} className="rounded-2xl border border-[#ffedd5] bg-white p-3.5">
                        <p className="text-[13px] font-bold text-[#1e293b]">{col.name}</p>
                        <div className="mt-1 flex items-center justify-between text-[12px] text-[#475569]">
                          <span>Trọng số: {col.weightPercent}%</span>
                          <span className="font-semibold text-[#ea580c]">Thang {col.maxScore}đ</span>
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              )}

              <div>
                <h4 className="mb-3 flex items-center gap-2 text-[14px] font-extrabold text-[#1e293b] uppercase">
                  <Clock className="h-4 w-4 text-[#ff5e62]" />
                  Danh sách lớp đang mở ({courseClasses.length} lớp)
                </h4>
                {courseClasses.length === 0 ? (
                  <p className="rounded-2xl border border-dashed border-[#fed7aa] bg-[#fffaf5] p-4 text-[13.5px] text-[#475569]">
                    Khoá học đang xếp lịch khai giảng đợt mới. Đăng ký tư vấn để giữ chỗ ưu tiên!
                  </p>
                ) : (
                  <div className="space-y-3">
                    {courseClasses.map((cls) => (
                      <LandingCourseClassRow
                        key={cls.id}
                        cls={cls}
                        onReserve={() =>
                          onConsultCourse({ courseId: course.id, branchId: cls.branchId, notes: `Giữ chỗ lớp ${cls.code}.` })
                        }
                      />
                    ))}
                  </div>
                )}
              </div>
            </div>

            <div className="mt-7 flex flex-col justify-end gap-3 border-t border-[#ffedd5] pt-5 sm:flex-row">
              <button type="button" onClick={onClose} className="cursor-pointer rounded-full border border-[#fed7aa] px-6 py-3 text-[13.5px] font-bold text-[#475569] hover:bg-[#fff7ed]">
                Đóng
              </button>
              <button
                type="button"
                onClick={() => onConsultCourse({ courseId: course.id })}
                className="inline-flex cursor-pointer items-center justify-center gap-2 rounded-full bg-gradient-to-r from-[#ff8c42] to-[#ff5e62] px-7 py-3 text-[13.5px] font-bold text-white shadow-[0_8px_20px_rgba(255,140,66,0.28)]"
              >
                <Sparkles className="h-4 w-4" />
                Đăng ký tư vấn khoá {course.code}
              </button>
            </div>
          </m.div>
        </div>
      )}
    </AnimatePresence>
  );
}
