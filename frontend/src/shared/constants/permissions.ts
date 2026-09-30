/** Bản sao của IdentityConstants.Resources ở backend. */
export const RESOURCE = {
  account: "ACCOUNT",
  role: "ROLE",
  group: "GROUP",
  permission: "PERMISSION",
  permissionGroup: "PERMISSION_GROUP",
  branch: "BRANCH",
  auditLog: "AUDIT_LOG",
  dashboard: "DASHBOARD",
} as const;

/** Bản sao của IdentityConstants.Actions. */
export const ACTION = {
  create: "CREATE",
  read: "READ",
  update: "UPDATE",
  delete: "DELETE",
  approve: "APPROVE",
} as const;

export const PERMISSION_SCOPE = {
  personal: "PERSONAL",
  branch: "BRANCH",
  organization: "ORGANIZATION",
} as const;

export type PermissionScope = (typeof PERMISSION_SCOPE)[keyof typeof PERMISSION_SCOPE];

/**
 * Thứ bậc scope, khớp PermissionScope.rank() ở backend: quyền scope rộng hơn thoả mãn được yêu cầu
 * hẹp hơn. Sai thứ tự ở đây thì giao diện sẽ mở nút mà API từ chối.
 */
export const PERMISSION_SCOPE_RANK: Record<PermissionScope, number> = {
  [PERMISSION_SCOPE.personal]: 0,
  [PERMISSION_SCOPE.branch]: 1,
  [PERMISSION_SCOPE.organization]: 2,
};

export const PERMISSION_SCOPE_LABEL: Record<PermissionScope, string> = {
  [PERMISSION_SCOPE.personal]: "Cá nhân",
  [PERMISSION_SCOPE.branch]: "Chi nhánh",
  [PERMISSION_SCOPE.organization]: "Toàn tổ chức",
};

export const RESOURCE_LABEL: Record<string, string> = {
  [RESOURCE.account]: "Tài khoản",
  [RESOURCE.role]: "Vai trò",
  [RESOURCE.group]: "Nhóm người dùng",
  [RESOURCE.permission]: "Quyền",
  [RESOURCE.permissionGroup]: "Nhóm quyền",
  [RESOURCE.branch]: "Chi nhánh",
  [RESOURCE.auditLog]: "Nhật ký",
  [RESOURCE.dashboard]: "Tổng quan",
};

export const ACTION_LABEL: Record<string, string> = {
  [ACTION.create]: "Tạo",
  [ACTION.read]: "Xem",
  [ACTION.update]: "Sửa",
  [ACTION.delete]: "Xoá",
  [ACTION.approve]: "Phê duyệt",
};

/**
 * Bản sao của IdentityConstants.AccessRules: yêu cầu quyền của từng nhóm endpoint. Mọi endpoint quản
 * trị đều đòi scope ORGANIZATION — một quyền ACCOUNT:UPDATE:PERSONAL chỉ đủ để tự sửa hồ sơ mình.
 * Giao diện phải hỏi đúng câu backend hỏi, nếu không sẽ có nút hiện ra rồi bấm vào là 403.
 */
export const ACCESS_RULE = {
  readDashboard: {
    resource: RESOURCE.dashboard,
    action: ACTION.read,
    scope: PERMISSION_SCOPE.organization,
  },
  readAccount: {
    resource: RESOURCE.account,
    action: ACTION.read,
    scope: PERMISSION_SCOPE.organization,
  },
  updateAccount: {
    resource: RESOURCE.account,
    action: ACTION.update,
    scope: PERMISSION_SCOPE.organization,
  },
  readRole: { resource: RESOURCE.role, action: ACTION.read, scope: PERMISSION_SCOPE.organization },
  createRole: { resource: RESOURCE.role, action: ACTION.create, scope: PERMISSION_SCOPE.organization },
  createPermissionGroup: {
    resource: RESOURCE.permissionGroup,
    action: ACTION.create,
    scope: PERMISSION_SCOPE.organization,
  },
  readBranch: { resource: RESOURCE.branch, action: ACTION.read, scope: PERMISSION_SCOPE.organization },
  createBranch: {
    resource: RESOURCE.branch,
    action: ACTION.create,
    scope: PERMISSION_SCOPE.organization,
  },
  updateBranch: {
    resource: RESOURCE.branch,
    action: ACTION.update,
    scope: PERMISSION_SCOPE.organization,
  },
} as const;
