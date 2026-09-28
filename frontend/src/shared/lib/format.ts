const LOCALE = "vi-VN";

const dateTimeFormat = new Intl.DateTimeFormat(LOCALE, {
  day: "2-digit",
  month: "2-digit",
  year: "numeric",
  hour: "2-digit",
  minute: "2-digit",
});

const numberFormat = new Intl.NumberFormat(LOCALE);

const relativeFormat = new Intl.RelativeTimeFormat(LOCALE, { numeric: "auto" });

const RELATIVE_STEPS: readonly { readonly limitMs: number; readonly unit: Intl.RelativeTimeFormatUnit }[] = [
  { limitMs: 60_000, unit: "second" },
  { limitMs: 3_600_000, unit: "minute" },
  { limitMs: 86_400_000, unit: "hour" },
  { limitMs: Number.POSITIVE_INFINITY, unit: "day" },
];

const UNIT_IN_MS: Record<string, number> = {
  second: 1_000,
  minute: 60_000,
  hour: 3_600_000,
  day: 86_400_000,
};

/** Hiển thị dữ liệu thời gian và số theo quy ước Việt Nam. */
export const formatter = {
  dateTime(isoInstant: string): string {
    return dateTimeFormat.format(new Date(isoInstant));
  },

  /** "3 phút trước" cho danh sách đăng nhập gần đây - dễ đọc hơn một mốc giờ tuyệt đối. */
  timeAgo(isoInstant: string): string {
    const elapsedMs = Date.now() - new Date(isoInstant).getTime();
    const step = RELATIVE_STEPS.find((candidate) => Math.abs(elapsedMs) < candidate.limitMs);
    if (!step) {
      return dateTimeFormat.format(new Date(isoInstant));
    }
    const divisor = UNIT_IN_MS[step.unit] ?? 1_000;
    return relativeFormat.format(-Math.round(elapsedMs / divisor), step.unit);
  },

  count(value: number): string {
    return numberFormat.format(value);
  },

  /** Chữ cái đầu của tên, dùng cho ảnh đại diện khi tài khoản chưa có avatar. */
  initials(fullName: string): string {
    const words = fullName.trim().split(/\s+/).filter((word) => word.length > 0);
    if (words.length === 0) {
      return "?";
    }
    const first = words[0] ?? "";
    const last = words.length > 1 ? (words[words.length - 1] ?? "") : "";
    return `${first.charAt(0)}${last.charAt(0)}`.toUpperCase();
  },
} as const;
