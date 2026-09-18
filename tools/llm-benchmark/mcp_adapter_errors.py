"""Typed errors raised while normalizing MCP tool results."""

from __future__ import annotations


class McpAdapterError(Exception):
    """Raised when an MCP result cannot be normalized into a safe page."""

    __slots__ = ("reason",)

    reason: str

    def __init__(self, reason: str) -> None:
        super().__init__(reason)
        self.reason = reason

    def __str__(self) -> str:
        return f"MCP adapter error: {self.reason}"


class McpScopeError(McpAdapterError):
    """Raised when a page is outside the connected Workspace range."""
