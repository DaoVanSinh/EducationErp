import { createContext, type ReactNode, useCallback, useContext, useMemo, useState } from "react";

const STORAGE_KEY = "eduerp.selectedBranchId";

export interface SelectedBranchState {
  /** null = "Toàn tổ chức" - không lọc theo chi nhánh nào. */
  readonly selectedBranchId: string | null;
  readonly setSelectedBranchId: (branchId: string | null) => void;
}

function readStoredBranchId(): string | null {
  try {
    return localStorage.getItem(STORAGE_KEY);
  } catch {
    return null;
  }
}

function writeStoredBranchId(branchId: string | null): void {
  try {
    if (branchId) {
      localStorage.setItem(STORAGE_KEY, branchId);
    } else {
      localStorage.removeItem(STORAGE_KEY);
    }
  } catch {
    // localStorage có thể bị chặn (private mode) - state trong bộ nhớ vẫn hoạt động bình thường.
  }
}

const BranchContext = createContext<SelectedBranchState>({
  selectedBranchId: null,
  setSelectedBranchId: () => {},
});

export interface SelectedBranchProviderProps {
  readonly children: ReactNode;
}

/**
 * Chi nhánh đang được xem trên toàn app (sidebar switcher, lọc danh sách tài khoản/dashboard). Giữ
 * trong localStorage chỉ để refresh trang không mất lựa chọn - không phải state nghiệp vụ dùng chung
 * giữa các thiết bị.
 */
export function SelectedBranchProvider({ children }: SelectedBranchProviderProps) {
  const [selectedBranchId, setSelectedBranchIdState] = useState<string | null>(readStoredBranchId);

  const setSelectedBranchId = useCallback((branchId: string | null) => {
    setSelectedBranchIdState(branchId);
    writeStoredBranchId(branchId);
  }, []);

  const value = useMemo(
    () => ({ selectedBranchId, setSelectedBranchId }),
    [selectedBranchId, setSelectedBranchId],
  );

  return <BranchContext.Provider value={value}>{children}</BranchContext.Provider>;
}

export function useSelectedBranch(): SelectedBranchState {
  return useContext(BranchContext);
}
