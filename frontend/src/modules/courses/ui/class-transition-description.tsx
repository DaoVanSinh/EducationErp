import type { ClassSummary } from "@/entities/class";
import { CLASS_TRANSITION, type ClassTransition } from "@/modules/courses/api/use-courses-mutations";

/** Giải thích hệ quả của từng thao tác chuyển trạng thái lớp trước khi admin xác nhận. */
export function ClassTransitionDescription({
  cls,
  transition,
}: {
  readonly cls: ClassSummary;
  readonly transition: ClassTransition;
}) {
  if (transition === CLASS_TRANSITION.start) {
    return (
      <p>
        Lớp chuyển sang <strong>Đang học</strong> với {cls.activeEnrollments} học viên. Từ lúc này giáo viên điểm danh,
        nhập điểm và tiến trình học tập bắt đầu được tính.
      </p>
    );
  }
  if (transition === CLASS_TRANSITION.complete) {
    return (
      <p>
        Lớp chuyển sang <strong>Đã kết thúc</strong>. {cls.activeEnrollments} ghi danh đang học sẽ được đánh dấu hoàn
        tất, lớp không nhận thêm học viên. Giáo viên vẫn sửa được điểm cuối khoá.
      </p>
    );
  }
  return (
    <p>
      Lớp chuyển sang <strong>Đã huỷ</strong>. {cls.activeEnrollments} ghi danh đang học sẽ chuyển sang đã rút. Hoá đơn
      đã phát hành không tự huỷ - hãy xử lý hoàn/huỷ ở màn Hoá đơn học phí.
    </p>
  );
}
