import { ACCOUNT_STATUS, type AccountSummary } from "@/entities/account";
import { ACCOUNT_COMMAND, type AccountCommand } from "@/modules/rbac/api/use-rbac-mutations";
import { ROLE_CODE } from "@/shared/constants/permissions";

/**
 * Vai trò không gán được bằng Đổi vai trò: Quản trị viên chỉ dành cho admin gốc, Giáo viên và Học viên có hồ sơ riêng
 * nên phải đóng tài khoản cũ rồi tạo mới ở đúng màn của họ.
 */
const ROLES_OUTSIDE_STAFF_FLOW: readonly string[] = [ROLE_CODE.admin, ROLE_CODE.teacher, ROLE_CODE.student];

export function isStaffRole(roleCode: string): boolean {
  return !ROLES_OUTSIDE_STAFF_FLOW.includes(roleCode);
}

/** Bộ lọc vai trò khi người dùng không đọc được danh mục vai trò (admin chi nhánh). */
export const FALLBACK_ROLE_FILTER_OPTIONS: readonly { readonly code: string; readonly name: string }[] = [
  { code: ROLE_CODE.teacher, name: "Giáo viên" },
  { code: ROLE_CODE.student, name: "Học viên" },
];

/** Khớp AccountLifecycleGuard: không ai đóng admin gốc, chỉ admin gốc xử lý được tài khoản quản trị chi nhánh. */
function isProtected(account: AccountSummary, canManageOrganization: boolean): boolean {
  if (account.roleCode === ROLE_CODE.admin) {
    return true;
  }
  return account.roleCode === ROLE_CODE.branchAdmin && !canManageOrganization;
}

function lifecycleCommands(account: AccountSummary): AccountCommand[] {
  if (account.lastLogin === null) {
    return [ACCOUNT_COMMAND.close];
  }
  return account.status === ACCOUNT_STATUS.disabled
    ? [ACCOUNT_COMMAND.enable, ACCOUNT_COMMAND.close]
    : [ACCOUNT_COMMAND.disable, ACCOUNT_COMMAND.close];
}

/** Lời mời và đăng xuất cưỡng bức là thao tác cấp tổ chức. */
function organizationCommands(account: AccountSummary): AccountCommand[] {
  if (account.lastLogin === null) {
    return account.status === ACCOUNT_STATUS.active
      ? [ACCOUNT_COMMAND.resendInvite, ACCOUNT_COMMAND.revokeInvite]
      : [ACCOUNT_COMMAND.resendInvite];
  }
  return account.status === ACCOUNT_STATUS.active ? [ACCOUNT_COMMAND.signOutEverywhere] : [];
}

/**
 * Lệnh nào áp dụng được cho một tài khoản - khớp đúng luật backend. Chính mình và tài khoản đã đóng thì không còn lệnh
 * nào; tài khoản đã đóng là vĩnh viễn, muốn quay lại thì tạo tài khoản mới cùng email.
 */
export function availableCommands(
  account: AccountSummary,
  currentAccountId: string | undefined,
  canManageOrganization: boolean,
): AccountCommand[] {
  if (account.id === currentAccountId || account.status === ACCOUNT_STATUS.closed) {
    return [];
  }
  const lifecycle = isProtected(account, canManageOrganization) ? [] : lifecycleCommands(account);
  return canManageOrganization ? [...organizationCommands(account), ...lifecycle] : lifecycle;
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
    description: "Mật khẩu tạm trong email không dùng được nữa và tài khoản bị tạm đóng. Có thể gửi lại lời mời sau.",
    tone: "danger",
  },
  [ACCOUNT_COMMAND.disable]: {
    label: "Tạm đóng tài khoản",
    title: "Tạm đóng tài khoản",
    confirmLabel: "Tạm đóng",
    description:
      "Người này bị đăng xuất khỏi mọi thiết bị ngay và không đăng nhập được cho tới khi mở lại. Dữ liệu giữ nguyên, giáo viên vẫn được tính lương.",
    tone: "danger",
  },
  [ACCOUNT_COMMAND.enable]: {
    label: "Mở lại tài khoản",
    title: "Mở lại tài khoản",
    confirmLabel: "Mở lại",
    description: "Người này đăng nhập lại được bằng mật khẩu của mình.",
    tone: "primary",
  },
  [ACCOUNT_COMMAND.close]: {
    label: "Đóng tài khoản",
    title: "Đóng tài khoản vĩnh viễn",
    confirmLabel: "Đóng tài khoản",
    description:
      "Không mở lại được. Dữ liệu cũ giữ nguyên và admin vẫn xem được, email được giải phóng để tạo tài khoản mới, giáo viên dừng tính lương. Bị chặn nếu giáo viên còn lớp mở, học viên còn đang học hoặc còn nợ học phí.",
    tone: "danger",
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
