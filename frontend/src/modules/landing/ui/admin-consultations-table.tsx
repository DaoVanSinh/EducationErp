import {
  CONSULTATION_STATUS,
  CONSULTATION_STATUS_LABEL,
  type ConsultationItem,
  type ConsultationStatus,
} from "@/modules/landing/model/landing-schema";
import { formatter } from "@/shared/lib/format";

interface AdminConsultationsTableProps {
  readonly items: readonly ConsultationItem[];
  readonly isLoading: boolean;
  readonly onStatusChange: (id: string, status: ConsultationStatus) => void;
}

const STATUS_OPTIONS: readonly ConsultationStatus[] = [
  CONSULTATION_STATUS.newLead,
  CONSULTATION_STATUS.contacted,
  CONSULTATION_STATUS.enrolled,
  CONSULTATION_STATUS.closed,
];

export function AdminConsultationsTable({
  items,
  isLoading,
  onStatusChange,
}: AdminConsultationsTableProps) {
  if (isLoading) {
    return (
      <div className="rounded-2xl border border-slate-200 bg-white p-8 text-center text-sm text-slate-500">
        Đang tải danh sách đăng ký tư vấn...
      </div>
    );
  }

  if (items.length === 0) {
    return (
      <div className="rounded-2xl border border-dashed border-slate-300 bg-white p-12 text-center">
        <p className="text-sm font-semibold text-slate-800">
          Chưa có đăng ký tư vấn nào từ Landing Page
        </p>
        <p className="mt-1 text-xs text-slate-500">
          Khi học viên điền form trên trang /landing, thông tin sẽ tự động hiển thị tại đây.
        </p>
      </div>
    );
  }

  return (
    <div className="overflow-hidden rounded-2xl border border-slate-200 bg-white">
      <div className="overflow-x-auto">
        <table className="min-w-full divide-y divide-slate-200 text-left text-sm">
          <thead className="bg-slate-50 text-xs font-semibold text-slate-600 uppercase">
            <tr>
              <th className="px-4 py-3">Học viên</th>
              <th className="px-4 py-3">Liên hệ</th>
              <th className="px-4 py-3">Khoá học / Cơ sở</th>
              <th className="px-4 py-3">Ghi chú</th>
              <th className="px-4 py-3">Thời gian</th>
              <th className="px-4 py-3">Trạng thái</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {items.map((item) => (
              <tr key={item.id} className="hover:bg-slate-50/70">
                <td className="px-4 py-3 font-semibold text-slate-900">{item.fullName}</td>
                <td className="px-4 py-3 text-xs text-slate-700">
                  <div className="font-bold text-orange-700">{item.phone}</div>
                  {item.email && <div className="text-slate-500">{item.email}</div>}
                </td>
                <td className="px-4 py-3 text-xs text-slate-700">
                  <div>{item.targetCourseName ?? "Chưa chọn khoá"}</div>
                  <div className="text-slate-500">{item.targetBranchName ?? "Chưa chọn cơ sở"}</div>
                </td>
                <td className="max-w-xs px-4 py-3 text-xs text-slate-600">
                  {item.notes ?? "—"}
                </td>
                <td className="px-4 py-3 text-xs text-slate-500">
                  {formatter.dateTime(item.createdAt)}
                </td>
                <td className="px-4 py-3">
                  <select
                    value={item.status}
                    aria-label={`Trạng thái tư vấn của ${item.fullName}`}
                    onChange={(e) =>
                      onStatusChange(item.id, e.target.value as ConsultationStatus)
                    }
                    className="rounded-lg border border-slate-300 bg-white px-2.5 py-1.5 text-xs font-semibold text-slate-800 focus:border-orange-500 focus:outline-none"
                  >
                    {STATUS_OPTIONS.map((st) => (
                      <option key={st} value={st}>
                        {CONSULTATION_STATUS_LABEL[st]}
                      </option>
                    ))}
                  </select>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
