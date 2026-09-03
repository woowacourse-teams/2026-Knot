#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.14"
# dependencies = ["pydantic", "pydantic-settings", "pytest"]
# ///

# ─── How to run ───
# 1. Install uv (if not installed):
#      curl -LsSf https://astral.sh/uv/install.sh | sh
# 2. Run directly:
#      uv run --with pydantic --with pydantic-settings --with pytest pytest tools/llm-benchmark/test_mcp_models.py
# ──────────────────

from __future__ import annotations

import pytest
from mcp_models import (
    FetchToolArguments,
    McpPage,
    McpScope,
    McpSettings,
    McpToolCallValidationError,
    NimFunctionCall,
    NimToolCall,
    SearchToolArguments,
    validate_nim_tool_call,
)
from pydantic import ValidationError


def test_scope_permits_only_the_active_workspace_snapshot_and_page_range() -> None:
    # Given: two pages that differ by workspace, snapshot, and allowlist membership
    scope = McpScope("workspace-a", "snapshot-2", frozenset({"page-allowed"}))
    allowed = McpPage(
        "page-allowed",
        "Allowed",
        "https://notion.so/page-allowed",
        "content",
        "workspace-a",
        "snapshot-2",
    )
    wrong_workspace = McpPage(
        "page-allowed",
        "Other workspace",
        "https://notion.so/page-allowed",
        "content",
        "workspace-b",
        "snapshot-2",
    )
    wrong_snapshot = McpPage(
        "page-allowed",
        "Old",
        "https://notion.so/page-allowed",
        "content",
        "workspace-a",
        "snapshot-1",
    )
    wrong_page = McpPage(
        "page-other",
        "Other",
        "https://notion.so/page-other",
        "content",
        "workspace-a",
        "snapshot-2",
    )

    # When & then: only the exact active scope is accepted
    assert scope.permits(allowed)
    assert not scope.permits(wrong_workspace)
    assert not scope.permits(wrong_snapshot)
    assert not scope.permits(wrong_page)


def test_scope_rejects_a_page_without_snapshot_proof_when_snapshot_is_active() -> None:
    # Given: a connected range pinned to one completed import snapshot
    scope = McpScope("workspace-a", "snapshot-2", frozenset({"page-allowed"}))
    missing_snapshot = McpPage(
        "page-allowed",
        "Missing provenance",
        "https://notion.so/page-allowed",
        "content",
        "workspace-a",
        None,
    )

    # When & then: missing snapshot provenance cannot be treated as the active one
    assert not scope.permits(missing_snapshot)


def test_validate_nim_tool_call_parses_search_alias_and_rejects_unknown_tool() -> None:
    # Given: a model call using the provider's JSON-string arguments
    call = NimToolCall.model_validate(
        {
            "id": "call-1",
            "type": "function",
            "function": {"name": "notion-search", "arguments": '{"q":"PostgreSQL"}'},
        }
    )

    # When & then: the read contract normalizes the alias
    validated = validate_nim_tool_call(call)
    assert isinstance(validated.arguments, SearchToolArguments)
    assert validated.arguments.query == "PostgreSQL"

    unknown = NimToolCall(
        id="call-2",
        function=NimFunctionCall(name="notion-update", arguments="{}"),
    )
    with pytest.raises(McpToolCallValidationError, match="not allowed"):
        validate_nim_tool_call(unknown)


def test_fetch_tool_arguments_require_a_page_reference_and_forbid_extra_actions() -> (
    None
):
    # Given: a fetch call without an identifier and a write-shaped extra field
    with pytest.raises(ValidationError):
        FetchToolArguments.model_validate({})

    # When & then: only read reference fields are accepted
    parsed = FetchToolArguments.model_validate({"id": "page-1", "cursor": "next"})
    assert parsed.page_id == "page-1"
    assert parsed.cursor == "next"
    with pytest.raises(ValidationError):
        FetchToolArguments.model_validate({"id": "page-1", "content": "overwrite"})


def test_read_only_mcp_settings_and_tool_calls_reject_write_contracts() -> None:
    # Given: settings and a model call that try to change the read-only boundary
    with pytest.raises(ValidationError):
        McpSettings(search_tool="notion-update")
    with pytest.raises(ValidationError):
        McpSettings(fetch_tool="notion-update")
    with pytest.raises(ValidationError):
        NimToolCall.model_validate(
            {
                "id": "call-1",
                "type": "function-call",
                "function": {"name": "notion-search", "arguments": "{}"},
            }
        )
