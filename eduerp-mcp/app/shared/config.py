"""Reusable field types for environment-driven settings sections."""

from ipaddress import IPv4Network, IPv6Network, ip_network
from typing import Annotated

from pydantic import BeforeValidator, SecretStr
from pydantic_settings import NoDecode

from app.shared.base.markers import helper


class ConfigParsers:
    """Parse comma-separated environment values."""

    @staticmethod
    @helper
    def csv_lower(value: object) -> object:
        """Split a comma list into trimmed lower-case items."""
        if isinstance(value, str):
            return [part.strip().lower() for part in value.split(",") if part.strip()]
        return value

    @staticmethod
    @helper
    def csv(value: object) -> object:
        """Split a comma list into trimmed items, keeping case."""
        if isinstance(value, str):
            return [part.strip() for part in value.split(",") if part.strip()]
        return value


class ConfigTypes:
    """Annotated list types read from a single comma-separated variable."""

    LowerCsv = Annotated[list[str], NoDecode, BeforeValidator(ConfigParsers.csv_lower)]
    Csv = Annotated[list[str], NoDecode, BeforeValidator(ConfigParsers.csv)]


class SettingsRules:
    """Generic validation of secret strength and proxy trust lists."""

    @staticmethod
    @helper
    def strong_secret(secret: SecretStr | None, minimum: int, distinct: int) -> bool:
        """Long enough and not trivially repetitive."""
        value = secret.get_secret_value() if secret else ""
        return len(value) >= minimum and len(set(value)) >= distinct

    @staticmethod
    @helper
    def proxy_networks(value: str) -> list[IPv4Network | IPv6Network]:
        """Parse a comma list of IPs/CIDRs; a malformed entry raises ValueError."""
        return [ip_network(part.strip()) for part in value.split(",")]

    @staticmethod
    @helper
    def trusts_everyone(networks: list[IPv4Network | IPv6Network]) -> bool:
        """A /0 network would trust every client as a proxy."""
        return any(network.prefixlen == 0 for network in networks)
