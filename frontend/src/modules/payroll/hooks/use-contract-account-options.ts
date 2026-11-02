import { useAccounts } from "@/entities/account";
import { ROLE_CODE } from "@/shared/constants/permissions";
import { useMemo } from "react";

/** Đủ cho một trung tâm; dropdown tham chiếu không cần phân trang (mirror các controller khác). */
const REFERENCE_PAGE_SIZE = 200;

/** Danh sách nhân viên để chọn khi tạo hợp đồng: mọi tài khoản trừ học viên, không riêng giáo viên. */
export function useContractAccountOptions() {
  const accounts = useAccounts(0, REFERENCE_PAGE_SIZE, null);

  const options = useMemo(
    () =>
      (accounts.data?.items ?? [])
        .filter((account) => account.roleCode !== ROLE_CODE.student)
        .map((account) => ({ id: account.id, label: `${account.fullName} · ${account.email}` })),
    [accounts.data],
  );

  return { options, error: accounts.error };
}
