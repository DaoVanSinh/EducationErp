import type { MyGradeItem } from "@/modules/learning/model/learning-schema";

export interface GradeListProps {
  readonly grades: readonly MyGradeItem[];
  readonly finalScore: number | null;
}

/** Điểm từng cột + tổng kết. Cột chưa chấm hiện "Chưa có điểm", không bao giờ in 0. */
export function GradeList({ grades, finalScore }: GradeListProps) {
  if (grades.length === 0) {
    return <p className="text-xs text-mist-400">Khóa học chưa cấu hình cột điểm.</p>;
  }

  return (
    <div className="flex flex-col gap-2">
      <ul className="grid grid-cols-1 gap-2 sm:grid-cols-2">
        {grades.map((grade) => (
          <li key={grade.columnId} className="glass flex items-center justify-between gap-3 rounded-xl px-3 py-2">
            <span className="truncate text-sm text-mist-100">
              {grade.name} <span className="text-xs text-mist-400">({grade.weightPercent}%)</span>
            </span>
            <GradeValue score={grade.score} maxScore={grade.maxScore} />
          </li>
        ))}
      </ul>
      <p className="text-sm text-mist-100">
        Điểm tổng kết (thang 100):{" "}
        <span className="font-semibold">{finalScore === null ? "Chưa đủ điểm các cột" : finalScore}</span>
      </p>
    </div>
  );
}

function GradeValue({ score, maxScore }: { readonly score: number | null; readonly maxScore: number }) {
  if (score === null) {
    return <span className="text-xs text-mist-400">Chưa có điểm</span>;
  }
  return (
    <span className="text-sm font-semibold text-mist-100">
      {score}/{maxScore}
    </span>
  );
}
