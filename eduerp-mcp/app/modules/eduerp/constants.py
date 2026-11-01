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

    class Tools:
        MY_CONTEXT = "eduerp_my_context"
        SEARCH_ENDPOINTS = "eduerp_search_endpoints"
        DESCRIBE_ENDPOINT = "eduerp_describe_endpoint"
        READ = "eduerp_read"
        WRITE = "eduerp_write"

    class AuditPrefixes:
        WRITE = "mcp.write."
