"""Typed errors shared by the MCP network and adapter boundaries."""

from __future__ import annotations


class McpTransportError(Exception):
    """Raised when the MCP endpoint cannot be reached or parsed."""

    __slots__ = ("reason",)

    reason: str

    def __init__(self, reason: str) -> None:
        super().__init__(reason)
        self.reason = reason

    def __str__(self) -> str:
        return f"MCP transport error: {self.reason}"


class McpHttpError(Exception):
    """Raised when the MCP endpoint returns an unsuccessful HTTP status."""

    __slots__ = ("detail", "status_code")

    status_code: int
    detail: str

    def __init__(self, status_code: int, detail: str) -> None:
        super().__init__(status_code, detail)
        self.status_code = status_code
        self.detail = detail

    def __str__(self) -> str:
        return f"MCP request failed with HTTP {self.status_code}: {self.detail}"


class McpProtocolError(Exception):
    """Raised when the MCP server returns a JSON-RPC error or invalid result."""

    __slots__ = ("code", "reason")

    code: int | None
    reason: str

    def __init__(self, reason: str, code: int | None = None) -> None:
        super().__init__(reason)
        self.code = code
        self.reason = reason

    def __str__(self) -> str:
        suffix = f" ({self.code})" if self.code is not None else ""
        return f"MCP protocol error{suffix}: {self.reason}"
