"""Constants for EduERP RBAC resources, actions, and scopes."""


class PermissionConstants:
    class Scopes:
        PERSONAL = "PERSONAL"
        BRANCH = "BRANCH"
        ORGANIZATION = "ORGANIZATION"

        RANK: dict[str, int] = {
            PERSONAL: 0,
            BRANCH: 1,
            ORGANIZATION: 2,
        }

    class Actions:
        READ = "READ"
        CREATE = "CREATE"
        UPDATE = "UPDATE"
        DELETE = "DELETE"
        APPROVE = "APPROVE"
        EXPORT = "EXPORT"

    class Resources:
        ACCOUNT = "ACCOUNT"
        ROLE = "ROLE"
        PERMISSION = "PERMISSION"
        BRANCH = "BRANCH"
        COURSE = "COURSE"
        CLASS = "CLASS"
        TEACHER = "TEACHER"
        STUDENT = "STUDENT"
        ENROLLMENT = "ENROLLMENT"
        INVOICE = "INVOICE"
        COMBO = "COMBO"
        GRADE = "GRADE"
        CONTRACT = "CONTRACT"
        PAYROLL = "PAYROLL"
        REPORT = "REPORT"

    class ErrorCodes:
        PERMISSION_DENIED = "access.permission_denied"
