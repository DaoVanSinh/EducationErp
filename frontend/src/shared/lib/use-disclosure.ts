import { useCallback, useState } from "react";

/** Trạng thái mở/đóng của một menu hoặc panel - để component UI không tự giữ state chuyển đổi. */
export function useDisclosure(initiallyOpen = false) {
  const [open, setOpen] = useState(initiallyOpen);
  return {
    open,
    onOpen: useCallback(() => setOpen(true), []),
    onClose: useCallback(() => setOpen(false), []),
    onToggle: useCallback(() => setOpen((previous) => !previous), []),
  };
}
