import { GlassPanel } from "@/shared/ui/glass-panel";

const RULES: readonly { readonly title: string; readonly lines: readonly string[] }[] = [
  {
    title: "Trả theo tháng",
    lines: [
      "Lương chính = Lương tháng / số ngày của tháng x (ngày chính thức + 85% x ngày thử việc)",
      "Phụ cấp = Tổng phụ cấp x ngày làm việc / số ngày của tháng",
      "BHXH = 10,5% x Lương chính, tối đa tính trên 50.600.000 đ",
      "Thuế = biểu lũy tiến 5 bậc trên (Thu nhập - BHXH - 15.500.000 đ - 6.200.000 đ x người phụ thuộc)",
    ],
  },
  {
    title: "Trả theo giờ",
    lines: [
      "Giờ dạy = tổng thời lượng các buổi đã điểm danh trong tháng",
      "Lương chính = Giờ dạy x Đơn giá",
      "Không có BHXH và phụ cấp",
      "Thuế = 10% x Thu nhập khi Thu nhập từ 5.000.000 đ, dưới mức này không khấu trừ",
    ],
  },
];

/** Công thức hiện ngay cạnh số liệu, để kế toán và người duyệt tự đối chiếu được từng phiếu. */
export function PayrollFormulaGuide() {
  return (
    <GlassPanel className="flex flex-col gap-3">
      <div>
        <h2 className="text-sm font-semibold text-mist-100">Cách tính</h2>
        <p className="text-xs text-mist-500">
          Thu nhập = Lương chính + Phụ cấp + Thưởng/điều chỉnh. Thực nhận = Thu nhập - BHXH - Thuế.
        </p>
      </div>
      <div className="grid grid-cols-1 gap-3 lg:grid-cols-2">
        {RULES.map((rule) => (
          <section key={rule.title} className="glass rounded-2xl p-3">
            <h3 className="text-sm font-medium text-mist-100">{rule.title}</h3>
            <ul className="mt-1 flex list-disc flex-col gap-1 pl-4 text-xs text-mist-400">
              {rule.lines.map((line) => (
                <li key={line}>{line}</li>
              ))}
            </ul>
          </section>
        ))}
      </div>
    </GlassPanel>
  );
}
