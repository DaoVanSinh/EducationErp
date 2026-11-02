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
        """Must match com.eduerp.modules.access.AccessConstants.Resources (seeded in `permissions`)."""

        ACCOUNT = "ACCOUNT"
        ROLE = "ROLE"
        BRANCH = "BRANCH"
        COURSE = "COURSE"
        CLASS = "CLASS"
        TEACHER = "TEACHER"
        STUDENT = "STUDENT"
        ENROLLMENT = "ENROLLMENT"
        INVOICE = "INVOICE"
        GRADE = "GRADE"
        PAYROLL = "PAYROLL"
        DASHBOARD = "DASHBOARD"
        PERMISSION_GROUP = "PERMISSION_GROUP"
        TEACHING = "TEACHING"
        LEARNING = "LEARNING"
        ATTENDANCE = "ATTENDANCE"
        ASSIGNMENT = "ASSIGNMENT"
        PRACTICE = "PRACTICE"

    class ErrorCodes:
        PERMISSION_DENIED = "access.permission_denied"
