import { ACCOUNT_STATUS, type AccountSummary } from "@/entities/account";
import { ACCOUNT_COMMAND, type AccountCommand } from "@/modules/rbac/api/use-rbac-mutations";

/**
 * Lệnh nào áp dụng được cho một tài khoản - khớp đúng luật backend:
 * - chưa kích hoạt lần nào: gửi lại hoặc thu hồi lời mời (mở lại bằng gửi lại lời mời, không phải mở khoá);
 * - đã kích hoạt, đang hoạt động: khoá, hoặc buộc đăng xuất mọi thiết bị;
 * - đã kích hoạt, đang bị khoá: mở khoá;
 * - chính mình: không khoá, không buộc đăng xuất (backend cũng chặn).
 */
export function availableCommands(account: AccountSummary, currentAccountId: string | undefined): AccountCommand[] {
  if (account.id === currentAccountId) {
    return [];
  }
  if (account.lastLogin === null) {
    return account.status === ACCOUNT_STATUS.active
      ? [ACCOUNT_COMMAND.resendInvite, ACCOUNT_COMMAND.revokeInvite]
      : [ACCOUNT_COMMAND.resendInvite];
  }
  if (account.status === ACCOUNT_STATUS.disabled) {
    return [ACCOUNT_COMMAND.enable];
  }
  return [ACCOUNT_COMMAND.signOutEverywhere, ACCOUNT_COMMAND.disable];
}

export const ACCOUNT_COMMAND_COPY: Record<
  AccountCommand,
  {
    readonly label: string;
    readonly title: string;
    readonly confirmLabel: string;
    readonly description: string;
    readonly tone: "primary" | "danger";
  }
> = {
  [ACCOUNT_COMMAND.resendInvite]: {
    label: "Gửi lại lời mời",
    title: "Gửi lại lời mời",
    confirmLabel: "Gửi lại",
    description: "Hệ thống tạo mật khẩu tạm mới (hiệu lực 7 ngày) và gửi lại email. Mật khẩu tạm cũ hết hiệu lực.",
    tone: "primary",
  },
  [ACCOUNT_COMMAND.revokeInvite]: {
    label: "Thu hồi lời mời",
    title: "Thu hồi lời mời",
    confirmLabel: "Thu hồi",
    description: "Mật khẩu tạm trong email không dùng được nữa và tài khoản bị khoá. Có thể gửi lại lời mời sau.",
    tone: "danger",
  },
  [ACCOUNT_COMMAND.disable]: {
    label: "Khoá tài khoản",
    title: "Khoá tài khoản",
    confirmLabel: "Khoá",
    description:
      "Người này bị đăng xuất khỏi mọi thiết bị ngay lập tức và không đăng nhập được nữa. Dữ liệu học tập, giảng dạy vẫn được giữ nguyên.",
    tone: "danger",
  },
  [ACCOUNT_COMMAND.enable]: {
    label: "Mở khoá",
    title: "Mở khoá tài khoản",
    confirmLabel: "Mở khoá",
    description: "Người này đăng nhập lại được bằng mật khẩu của mình.",
    tone: "primary",
  },
  [ACCOUNT_COMMAND.signOutEverywhere]: {
    label: "Đăng xuất mọi thiết bị",
    title: "Đăng xuất khỏi mọi thiết bị",
    confirmLabel: "Đăng xuất",
    description:
      "Mọi phiên đăng nhập hiện có của người này bị thu hồi ngay. Tài khoản vẫn hoạt động - dùng khi nghi lộ mật khẩu.",
    tone: "primary",
  },
};
