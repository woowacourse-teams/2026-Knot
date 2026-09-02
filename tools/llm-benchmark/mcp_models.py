"""Typed contracts shared by the replay and live Notion MCP adapters."""

from __future__ import annotations

import json
from dataclasses import dataclass
from enum import StrEnum
from typing import Final, Protocol, assert_never

from pydantic import (
    AliasChoices,
    BaseModel,
    ConfigDict,
    Field,
    SecretStr,
    ValidationError,
    field_validator,
    model_validator,
)
from pydantic_settings import BaseSettings, SettingsConfigDict
from typing_extensions import TypeAliasType

JsonValue = TypeAliasType(
    "JsonValue",
    str | int | float | bool | None | list["JsonValue"] | dict[str, "JsonValue"],
)
JsonObject = dict[str, JsonValue]


class McpToolName(StrEnum):
    """Read-only Notion MCP tools used by the benchmark."""

    SEARCH = "notion-search"
    FETCH = "notion-fetch"


class McpSettings(BaseSettings):
    """MCP settings parsed at the environment boundary without exposing secrets."""

    model_config = SettingsConfigDict(env_prefix="NOTION_MCP_", extra="ignore", frozen=True)

    endpoint_url: str = "https://mcp.notion.com/mcp"
    access_token: SecretStr = SecretStr("")
    protocol_version: str = "2025-11-25"
    search_tool: str = McpToolName.SEARCH.value
    fetch_tool: str = McpToolName.FETCH.value
    connect_timeout_s: float = Field(default=10.0, gt=0)
    read_timeout_s: float = Field(default=90.0, gt=0)
    max_retries: int = Field(default=2, ge=0, le=5)
    retry_backoff_s: float = Field(default=0.25, ge=0)


@dataclass(frozen=True, slots=True)
class McpScope:
    """The connected Workspace and the exact active page range allowed to read."""

    workspace_id: str
    active_snapshot_id: str | None
    allowed_page_ids: frozenset[str]

    def __post_init__(self) -> None:
        object.__setattr__(
            self,
            "allowed_page_ids",
            frozenset(_normalize_page_id(page_id) for page_id in self.allowed_page_ids),
        )

    def permits(self, page: McpPage) -> bool:
        """Return whether a page belongs to this connected active range."""
        return (
            page.workspace_id == self.workspace_id
            and (self.active_snapshot_id is None or page.snapshot_id == self.active_snapshot_id)
            and _normalize_page_id(page.page_id) in self.allowed_page_ids
        )


@dataclass(frozen=True, slots=True)
class McpPage:
    """A normalized page with enough metadata to prove scope and provenance."""

    page_id: str
    title: str
    url: str
    content: str
    workspace_id: str
    snapshot_id: str | None
    parent_page_id: str | None = None
    last_edited_time: str | None = None


@dataclass(frozen=True, slots=True)
class McpSearchHit:
    """A search result before the detail fetch."""

    page_id: str
    title: str
    url: str
    snippet: str
    workspace_id: str
    snapshot_id: str | None
    last_edited_time: str | None = None


@dataclass(frozen=True, slots=True)
class McpToolTrace:
    """Observable MCP timing and retry counters for one logical operation."""

    operation: str
    tool_name: str
    elapsed_ms: float
    http_requests: int
    retry_count: int
    rate_limit_count: int
    page_count: int


@dataclass(frozen=True, slots=True)
class McpSearchResult:
    """Scope-filtered search hits and their transport trace."""

    hits: tuple[McpSearchHit, ...]
    trace: McpToolTrace


@dataclass(frozen=True, slots=True)
class McpFetchResult:
    """Scope-filtered page detail and its transport trace."""

    page: McpPage
    trace: McpToolTrace


class McpReadAdapter(Protocol):
    """Read-only contract implemented by replay and live Notion adapters."""

    def search(self, query: str, limit: int) -> McpSearchResult: ...

    def fetch(self, hit: McpSearchHit) -> McpFetchResult: ...


class McpContentBlock(BaseModel):
    """One MCP result content block; unknown future fields are ignored."""

    model_config = ConfigDict(extra="ignore", frozen=True)

    type: str
    text: str | None = None


class McpToolResult(BaseModel):
    """MCP tool result parsed at the untrusted protocol boundary."""

    model_config = ConfigDict(extra="ignore", frozen=True, populate_by_name=True)

    content: tuple[McpContentBlock, ...] = ()
    is_error: bool = Field(default=False, alias="isError")
    structured_content: JsonObject | None = Field(default=None, alias="structuredContent")


class McpRpcError(BaseModel):
    """JSON-RPC error returned by the MCP endpoint."""

    model_config = ConfigDict(extra="ignore", frozen=True)

    code: int
    message: str
    data: JsonValue | None = None


class McpRpcResponse(BaseModel):
    """JSON-RPC response envelope used by Streamable HTTP and SSE."""

    model_config = ConfigDict(extra="ignore", frozen=True)

    jsonrpc: str
    id: int | str | None = None
    result: JsonObject | None = None
    error: McpRpcError | None = None


class NimFunctionCall(BaseModel):
    """OpenAI-compatible function call returned by a model."""

    model_config = ConfigDict(extra="ignore", frozen=True)

    name: str
    arguments: str | JsonObject


class NimToolCall(BaseModel):
    """Tool call payload that must be validated before reaching MCP."""

    model_config = ConfigDict(extra="ignore", frozen=True)

    id: str = Field(min_length=1)
    type: str = "function"
    function: NimFunctionCall


class SearchToolArguments(BaseModel):
    """Validated search arguments accepted from the model."""

    model_config = ConfigDict(extra="forbid", frozen=True, populate_by_name=True)

    query: str = Field(
        min_length=1,
        max_length=1_000,
        validation_alias=AliasChoices("query", "q"),
    )

    @field_validator("query")
    @classmethod
    def query_must_not_be_blank(cls, value: str) -> str:
        normalized = value.strip()
        if not normalized:
            raise McpArgumentError("query must not be blank")
        return normalized


class FetchToolArguments(BaseModel):
    """Validated page detail arguments accepted from the model."""

    model_config = ConfigDict(extra="forbid", frozen=True, populate_by_name=True)

    page_id: str | None = Field(default=None, validation_alias=AliasChoices("page_id", "id"))
    url: str | None = None
    cursor: str | None = None

    @model_validator(mode="after")
    def require_page_reference(self) -> FetchToolArguments:
        if not self.page_id and not self.url:
            raise McpArgumentError("page_id or url is required")
        return self


@dataclass(frozen=True, slots=True)
class ValidatedToolCall:
    """A model tool call whose name and arguments passed the MCP boundary."""

    call_id: str
    tool: McpToolName
    arguments: SearchToolArguments | FetchToolArguments


class McpToolCallValidationError(Exception):
    """Raised when a model requests a tool or argument outside the read contract."""

    __slots__ = ("reason",)

    reason: str

    def __init__(self, reason: str) -> None:
        super().__init__(reason)
        self.reason = reason

    def __str__(self) -> str:
        return f"invalid MCP tool call: {self.reason}"


class McpArgumentError(ValueError):
    """Raised by Pydantic validators for malformed MCP arguments."""

    __slots__ = ("reason",)

    reason: str

    def __init__(self, reason: str) -> None:
        super().__init__(reason)
        self.reason = reason

    def __str__(self) -> str:
        return self.reason


def validate_nim_tool_call(
    call: NimToolCall,
    *,
    search_tool: str = McpToolName.SEARCH.value,
    fetch_tool: str = McpToolName.FETCH.value,
) -> ValidatedToolCall:
    """Validate a model call before executing a read-only Notion operation."""
    try:
        arguments = _arguments_object(call.function.arguments)
        tool = {search_tool: McpToolName.SEARCH, fetch_tool: McpToolName.FETCH}.get(call.function.name)
        if tool is None:
            raise McpToolCallValidationError(f"tool {call.function.name!r} is not allowed")
        match tool:
            case McpToolName.SEARCH:
                parsed = SearchToolArguments.model_validate(arguments)
            case McpToolName.FETCH:
                parsed = FetchToolArguments.model_validate(arguments)
            case unreachable:
                assert_never(unreachable)
        return ValidatedToolCall(call.id, tool, parsed)
    except (McpArgumentError, ValidationError, json.JSONDecodeError, TypeError) as error:
        raise McpToolCallValidationError("tool arguments do not match the read contract") from error


def _arguments_object(raw: str | JsonObject) -> JsonObject:
    if isinstance(raw, str):
        decoded = json.loads(raw)
        if not isinstance(decoded, dict):
            raise McpArgumentError("tool arguments must be a JSON object")
        return {key: value for key, value in decoded.items() if isinstance(key, str)}
    return raw


def _normalize_page_id(page_id: str) -> str:
    return page_id.casefold().replace("-", "")


MCP_DEFAULT_PAGE_LIMIT: Final[int] = 10
