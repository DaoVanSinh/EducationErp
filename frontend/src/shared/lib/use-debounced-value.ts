import { useEffect, useState } from "react";

/** Giá trị trễ cho ô tìm kiếm: gõ liên tục chỉ tạo một truy vấn sau khi người dùng dừng. */
export function useDebouncedValue<T>(value: T, delayMs: number): T {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const timer = window.setTimeout(() => setDebounced(value), delayMs);
    return () => window.clearTimeout(timer);
  }, [value, delayMs]);
  return debounced;
}
