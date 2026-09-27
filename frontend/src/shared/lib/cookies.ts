/** Đọc cookie không HttpOnly. Chỉ có một khách hàng thật sự: cookie CSRF do Spring ghi. */
export const cookies = {
  read(name: string): string | null {
    const prefix = `${name}=`;
    const entry = document.cookie.split("; ").find((candidate) => candidate.startsWith(prefix));
    return entry ? decodeURIComponent(entry.slice(prefix.length)) : null;
  },
} as const;
