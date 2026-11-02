"""Constants for the EduERP MCP module."""


class EduErpModuleConstants:
    MODULE_CODE = "eduerp"
    SERVER_NAME = "EduERP Core MCP"
    ROUTE_PREFIX = "eduerp"

    class Scopes:
        PERSONAL = "PERSONAL"
        BRANCH = "BRANCH"
        ORGANIZATION = "ORGANIZATION"

    class Actions:
        READ = "READ"
        CREATE = "CREATE"
        UPDATE = "UPDATE"
        APPROVE = "APPROVE"

    class Resources:
        """Must match com.eduerp.modules.access.AccessConstants.Resources (seeded in `permissions`).

        Combos, discount tiers and group registrations are guarded by INVOICE; contracts and
        payroll runs by PAYROLL; the RBAC catalog by ROLE - exactly as the backend does.
        """

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

    class Tools:
        MY_CONTEXT = "eduerp_my_context"
        SEARCH_ENDPOINTS = "eduerp_search_endpoints"
        DESCRIBE_ENDPOINT = "eduerp_describe_endpoint"
        READ = "eduerp_read"
        WRITE = "eduerp_write"

    class AuditPrefixes:
        WRITE = "mcp.write."
