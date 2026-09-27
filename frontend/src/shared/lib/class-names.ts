/** Ghép class có điều kiện. Nhận false/undefined để viết `cx("a", isOpen && "b")`. */
export function cx(...parts: readonly (string | false | null | undefined)[]): string {
  return parts.filter((part): part is string => typeof part === "string" && part.length > 0).join(" ");
}
