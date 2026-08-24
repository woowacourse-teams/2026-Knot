#!/usr/bin/env python3
"""Plan a Knot Issue contract without mutating GitHub or the repository."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
from pathlib import Path
from typing import Any

import adr_contract


HIGH_RISK_SIGNALS = {
    "data",
    "security",
    "external",
    "cross-boundary",
    "core-flow",
    "shared",
    "hard-to-reverse",
}

ISSUE_H2_HEADINGS = ("## 구현 기능 설명", "## TODO", "## 메모")
MARKDOWN_H2_PATTERN = re.compile(r"(?m)^[ \t]{0,3}##(?:[ \t]+|$)")

LIGHTWEIGHT_REQUIRED = (
    "title",
    "purpose",
    "scope",
    "acceptance_criteria",
    "verification",
    "evidence",
)

HARNESSED_REQUIRED = (
    "non_goals",
    "normal_flows",
    "failure_flows",
    "recovery_flows",
    "impacts",
    "dependencies",
    "residual_risks",
    "grill",
    "adr",
)

ADR_REQUIRED = (
    "status",
    "decision",
    "context",
    "situation",
    "decision_drivers",
    "alternatives",
    "alternatives_confirmed",
    "long_term_impact",
    "future_reference",
    "rationale",
    "consequences",
    "revisit_when",
    "planned_path",
)

LIST_FIELDS = (
    "scope",
    "acceptance_criteria",
    "verification",
    "evidence",
    "risk_signals",
    "non_goals",
    "normal_flows",
    "failure_flows",
    "recovery_flows",
    "impacts",
    "dependencies",
    "residual_risks",
)

ADR_STRING_FIELDS = (
    "status",
    "decision",
    "context",
    "situation",
    "rationale",
    "planned_path",
)

ADR_LIST_FIELDS = (
    "decision_drivers",
    "alternatives",
    "consequences",
    "revisit_when",
)

ADR_TRUE_FIELDS = (
    "alternatives_confirmed",
    "long_term_impact",
    "future_reference",
)


def _is_nonblank_string(value: Any) -> bool:
    return isinstance(value, str) and bool(value.strip())


def _has_value(value: Any) -> bool:
    if isinstance(value, str):
        return bool(value.strip())
    if isinstance(value, list):
        return bool(value)
    if isinstance(value, dict):
        return bool(value)
    return value is not None


def _missing_fields(snapshot: dict[str, Any], fields: tuple[str, ...]) -> list[str]:
    return [field for field in fields if not _has_value(snapshot.get(field))]


def _missing_keys(snapshot: dict[str, Any], fields: tuple[str, ...]) -> list[str]:
    return [field for field in fields if field not in snapshot]


def _string_list_errors(field: str, value: Any) -> list[str]:
    if not isinstance(value, list):
        return [f"{field} must be a list"]
    if not all(_is_nonblank_string(item) for item in value):
        return [f"{field} must contain non-empty strings"]
    return []


def _markdown_h2_errors(value: Any, field: str = "snapshot") -> list[str]:
    if isinstance(value, str):
        if MARKDOWN_H2_PATTERN.search(value):
            return [f"{field} must not contain Markdown level-2 headings"]
        return []
    if isinstance(value, list):
        errors: list[str] = []
        for index, item in enumerate(value):
            errors.extend(_markdown_h2_errors(item, f"{field}[{index}]"))
        return errors
    if isinstance(value, dict):
        errors = []
        for key, item in value.items():
            errors.extend(_markdown_h2_errors(item, f"{field}.{key}"))
        return errors
    return []


def _requested_action(snapshot: Any) -> str:
    if not isinstance(snapshot, dict):
        return "unknown"
    if snapshot.get("operation") == "create":
        return "publish_issue"
    if snapshot.get("operation") == "draft":
        return "render_draft"
    return "unknown"


def _issue_h2_headings(markdown: str) -> tuple[str, ...]:
    return tuple(line for line in markdown.splitlines() if line.startswith("## "))


def classify_risk(snapshot: dict[str, Any]) -> tuple[str, list[str]]:
    if "risk_signals" not in snapshot:
        return "unknown", ["missing: risk_signals"]

    signals = snapshot["risk_signals"]
    if not isinstance(signals, list):
        return "unknown", ["risk_signals must be a list"]
    if not all(_is_nonblank_string(signal) for signal in signals):
        return "unknown", ["risk_signals must contain non-empty strings"]

    unknown = sorted(set(signals) - HIGH_RISK_SIGNALS)
    if unknown:
        return "unknown", [f"unknown risk signal: {signal}" for signal in unknown]
    return ("high" if signals else "low"), []


def validate(snapshot: Any) -> tuple[str, list[str]]:
    if not isinstance(snapshot, dict):
        return "unknown", ["snapshot must be an object"]

    errors: list[str] = []
    operation = snapshot.get("operation")
    if not isinstance(operation, str) or operation not in {"draft", "create"}:
        errors.append("operation must be draft or create")

    title = snapshot.get("title")
    if title is not None and not isinstance(title, str):
        errors.append("title must be a string")
    elif isinstance(title, str) and title.strip() and not re.match(r"^\[(BE|FE)\] \S", title):
        errors.append("title must start with [BE] or [FE]")

    purpose = snapshot.get("purpose")
    if purpose is not None and not isinstance(purpose, str):
        errors.append("purpose must be a string")

    for field in LIST_FIELDS:
        if field in snapshot and field != "risk_signals":
            errors.extend(_string_list_errors(field, snapshot[field]))

    risk_level, risk_errors = classify_risk(snapshot)
    errors.extend(risk_errors)
    errors.extend(f"missing: {field}" for field in _missing_fields(snapshot, LIGHTWEIGHT_REQUIRED))
    errors.extend(_markdown_h2_errors(snapshot))

    if risk_level == "high":
        for field in _missing_keys(snapshot, HARNESSED_REQUIRED):
            errors.append(f"missing: {field}")

        for field in ("non_goals", "normal_flows", "failure_flows", "recovery_flows", "impacts"):
            if field in snapshot and not _has_value(snapshot[field]):
                errors.append(f"missing: {field}")

        grill = snapshot.get("grill")
        if grill is not None and not isinstance(grill, dict):
            errors.append("grill must be an object")
        elif isinstance(grill, dict):
            if grill.get("status") != "pass":
                errors.append("grill.status must be pass")
            resolved_questions = grill.get("resolved_questions")
            if not _has_value(resolved_questions):
                errors.append("missing: grill.resolved_questions")
            elif not isinstance(resolved_questions, list):
                errors.append("grill.resolved_questions must be a list")
            else:
                errors.extend(
                    _string_list_errors("grill.resolved_questions", resolved_questions)
                )

        adr = snapshot.get("adr")
        if adr is not None and not isinstance(adr, dict):
            errors.append("adr must be an object")
        elif isinstance(adr, dict):
            required = adr.get("required")
            if required is True:
                for field in _missing_fields(adr, ADR_REQUIRED):
                    errors.append(f"missing: adr.{field}")
                for field in ADR_STRING_FIELDS:
                    if field in adr and not isinstance(adr[field], str):
                        errors.append(f"adr.{field} must be a string")
                for field in ADR_LIST_FIELDS:
                    if field in adr:
                        errors.extend(_string_list_errors(f"adr.{field}", adr[field]))
                if isinstance(adr.get("alternatives"), list) and len(adr["alternatives"]) < 2:
                    errors.append("adr.alternatives must include at least 2 confirmed real alternatives")
                for field in ADR_TRUE_FIELDS:
                    if field in adr and adr.get(field) is not True:
                        errors.append(f"adr.{field} must be true")
                if "status" in adr and adr.get("status") != "proposed":
                    errors.append("adr.status must be proposed")
                if "decision_makers" in adr and not _is_nonblank_string(adr["decision_makers"]):
                    errors.append("adr.decision_makers must be a non-empty string")
                errors.extend(validate_adr_path(adr.get("planned_path")))
            elif required is False:
                reason = adr.get("reason")
                if not _has_value(reason):
                    errors.append("missing: adr.reason")
                elif not isinstance(reason, str):
                    errors.append("adr.reason must be a string")
            else:
                errors.append("adr.required must be true or false")

    return risk_level, sorted(set(errors))


def contract_id(snapshot: Any) -> str:
    canonical = json.dumps(snapshot, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    return hashlib.sha256(canonical.encode("utf-8")).hexdigest()[:16]


def _markdown_items(values: list[str]) -> str:
    return "\n".join(f"- {value}" for value in values) if values else "- 없음"


def _checkbox_items(values: list[str]) -> str:
    return "\n".join(f"- [ ] {value}" for value in values) if values else "- [ ] 없음"


def validate_adr_path(planned_path: Any) -> list[str]:
    return adr_contract.validate_planned_path(planned_path)


def render_issue_body(snapshot: dict[str, Any], identifier: str, risk_level: str) -> str:
    adr = snapshot.get("adr")
    adr_required = risk_level == "high" and isinstance(adr, dict) and adr.get("required") is True
    description = snapshot["purpose"]
    if adr_required:
        description = "\n\n".join([adr["situation"], snapshot["purpose"]])

    todo_items = list(snapshot["scope"]) + [f"검증: {check}" for check in snapshot["verification"]]
    memo_items = ["없음"]
    if adr_required:
        memo_items = [
            f"ADR: {adr['decision']} — 예정 경로: `{adr['planned_path']}` (Proposed)",
        ]

    sections = [
        f"<!-- knot-issue-contract:{identifier} -->",
        "## 구현 기능 설명",
        description,
        "## TODO",
        _checkbox_items(todo_items),
        "## 메모",
        _markdown_items(memo_items),
    ]

    return "\n\n".join(sections) + "\n"


def plan(snapshot: Any) -> dict[str, Any]:
    risk_level, errors = validate(snapshot)
    identifier = contract_id(snapshot)
    requested_action = _requested_action(snapshot)
    if errors:
        return {
            "status": "hold",
            "action": "report_hold",
            "requested_action": requested_action,
            "risk_level": risk_level,
            "remote_write_authorized": False,
            "publish_ready": False,
            "contract_id": identifier,
            "errors": errors,
        }

    operation = snapshot["operation"]
    adr = snapshot.get("adr", {})
    issue_body = render_issue_body(snapshot, identifier, risk_level)
    if _issue_h2_headings(issue_body) != ISSUE_H2_HEADINGS:
        return {
            "status": "hold",
            "action": "report_hold",
            "requested_action": requested_action,
            "risk_level": risk_level,
            "remote_write_authorized": False,
            "publish_ready": False,
            "contract_id": identifier,
            "errors": ["rendered Issue must contain exactly the three allowed level-2 headings"],
        }
    return {
        "status": "pass",
        "action": "render_draft",
        "requested_action": requested_action,
        "risk_level": risk_level,
        "remote_write_authorized": False,
        "publish_ready": operation == "create",
        "contract_id": identifier,
        "next_on_implementation": (
            "materialize_proposed_adr" if isinstance(adr, dict) and adr.get("required") is True else "none"
        ),
        "issue_body": issue_body,
    }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("snapshot", type=Path, help="Issue contract JSON snapshot")
    parser.add_argument("--pretty", action="store_true", help="Pretty-print JSON output")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    try:
        snapshot = json.loads(args.snapshot.read_text(encoding="utf-8"))
        result = plan(snapshot)
    except (OSError, json.JSONDecodeError) as error:
        result = {
            "status": "hold",
            "action": "report_hold",
            "requested_action": "unknown",
            "risk_level": "unknown",
            "remote_write_authorized": False,
            "publish_ready": False,
            "contract_id": contract_id({"invalid_snapshot": str(error)}),
            "errors": [f"snapshot could not be read as JSON: {error}"],
        }
    print(json.dumps(result, ensure_ascii=False, indent=2 if args.pretty else None, sort_keys=True))
    return 0 if result["status"] == "pass" else 1


if __name__ == "__main__":
    raise SystemExit(main())
