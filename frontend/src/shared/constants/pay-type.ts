/** Khớp TeachersConstants.PayType ở backend. Hai entity teacher và payroll cùng dùng nên đặt ở shared. */
export const PAY_TYPE = {
  monthly: "MONTHLY",
  hourly: "HOURLY",
} as const;

export type PayType = (typeof PAY_TYPE)[keyof typeof PAY_TYPE];

export const PAY_TYPE_LABEL: Record<PayType, string> = {
  [PAY_TYPE.monthly]: "Lương tháng",
  [PAY_TYPE.hourly]: "Theo giờ dạy",
};
