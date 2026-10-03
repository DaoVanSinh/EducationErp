import { CONTRACT_TYPE_LABEL, type Payslip } from "@/entities/payroll";
import type { UpdatePayslipPayload } from "@/entities/payroll";
import { GlassInput } from "@/shared/ui/glass-input";

export interface PayslipsTableProps {
  readonly rows: readonly Payslip[];
  readonly editable: boolean;
  readonly onUpdate: (payslipId: string, payload: UpdatePayslipPayload) => void;
}

/** Sửa được tại chỗ khi editable (PayrollRun.status === DRAFT) - echo thay đổi ra ngoài qua onUpdate,
 * không tự gọi API (Mandate #2: logic mutation nằm ở controller hook, không ở đây). */
export function PayslipsTable({ rows, editable, onUpdate }: PayslipsTableProps) {
  return (
    <table className="w-full text-sm">
      <thead>
        <tr className="text-left text-xs uppercase text-mist-500">
          <th>Nhân viên</th>
          <th>Loại HĐ</th>
          <th>Giờ dạy</th>
          <th>Lương gộp</th>
          <th>BHXH (NLĐ)</th>
          <th>Thuế TNCN</th>
          <th>Thực nhận</th>
        </tr>
      </thead>
      <tbody>
        {rows.map((payslip) => (
          <tr key={payslip.id} className="border-t border-white/10">
            <td>{payslip.accountFullName}</td>
            <td>{CONTRACT_TYPE_LABEL[payslip.contractType]}</td>
            <td>
              {editable && payslip.contractType === "COLLABORATOR" ? (
                <GlassInput
                  type="number"
                  value={payslip.hoursWorked ?? ""}
                  onChange={(event) =>
                    onUpdate(payslip.id, {
                      hoursWorked: Number(event.target.value),
                      incomeTaxWithheld: payslip.incomeTaxWithheld,
                      note: null,
                    })
                  }
                />
              ) : (
                (payslip.hoursWorked ?? "-")
              )}
            </td>
            <td>{payslip.grossPay.toLocaleString("vi-VN")}</td>
            <td>{payslip.socialInsuranceEmployee.toLocaleString("vi-VN")}</td>
            <td>
              {editable ? (
                <GlassInput
                  type="number"
                  value={payslip.incomeTaxWithheld}
                  onChange={(event) =>
                    onUpdate(payslip.id, {
                      hoursWorked: payslip.hoursWorked,
                      incomeTaxWithheld: Number(event.target.value),
                      note: null,
                    })
                  }
                />
              ) : (
                payslip.incomeTaxWithheld.toLocaleString("vi-VN")
              )}
            </td>
            <td>{payslip.netPay.toLocaleString("vi-VN")}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
