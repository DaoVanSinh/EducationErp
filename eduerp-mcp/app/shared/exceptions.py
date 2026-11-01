"""Exceptions for the application."""

from __future__ import annotations

from typing import Any

from app.shared.constants import CommonErrorCodes, CommonErrorMessages


class BusinessException(ValueError):
    """Base exception for all business logic errors."""

    def __init__(
        self,
        status_code: int = 400,
        error_code: str = CommonErrorCodes.BAD_REQUEST,
        message: str = CommonErrorMessages.INTERNAL_ERROR,
        detail: str | None = None,
        context: dict[str, Any] | None = None,
    ) -> None:
        self.status_code = status_code
        self.error_code = error_code
        self.message = detail or message
        self.detail = self.message
        self.context = context or {}
        super().__init__(self.message)


class BadRequestError(BusinessException):
    def __init__(
        self,
        message: str = CommonErrorMessages.VALIDATION_FAILED,
        error_code: str = CommonErrorCodes.BAD_REQUEST,
    ) -> None:
        super().__init__(status_code=400, error_code=error_code, message=message)


class AuthenticationError(BusinessException):
    def __init__(
        self,
        message: str = CommonErrorMessages.UNAUTHORIZED,
        error_code: str = CommonErrorCodes.UNAUTHORIZED,
    ) -> None:
        super().__init__(status_code=401, error_code=error_code, message=message)


class PermissionDeniedError(BusinessException):
    def __init__(
        self,
        message: str = CommonErrorMessages.FORBIDDEN,
        error_code: str = CommonErrorCodes.FORBIDDEN,
    ) -> None:
        super().__init__(status_code=403, error_code=error_code, message=message)


class NotFoundError(BusinessException):
    def __init__(
        self,
        message: str = CommonErrorMessages.NOT_FOUND,
        error_code: str = CommonErrorCodes.NOT_FOUND,
    ) -> None:
        super().__init__(status_code=404, error_code=error_code, message=message)


class ConflictError(BusinessException):
    def __init__(
        self, message: str = "Conflict", error_code: str = CommonErrorCodes.CONFLICT
    ) -> None:
        super().__init__(status_code=409, error_code=error_code, message=message)


class ValidationError(BusinessException):
    def __init__(
        self,
        message: str = CommonErrorMessages.VALIDATION_FAILED,
        error_code: str = CommonErrorCodes.VALIDATION_FAILED,
    ) -> None:
        super().__init__(status_code=422, error_code=error_code, message=message)


class Forbidden(PermissionDeniedError):
    """Hub scope denial; retain the existing ValueError catch contract."""


class NotFound(NotFoundError):
    """Resource unavailable within the caller's scope."""
