#!/usr/bin/env python3
"""Materialize a Proposed ADR file from a validated Knot Issue snapshot."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any

import adr_contract
import issue_planning


def _load_snapshot(snapshot_or_path: Any) -> Any:
    if isinstance(snapshot_or_path, (str, Path)):
        return json.loads(Path(snapshot_or_path).read_text(encoding="utf-8"))
    return snapshot_or_path


def _repo_root(path: Path | None) -> Path:
    return path.resolve() if path is not None else Path(__file__).resolve().parents[1]


def _target_path(repo_root: Path, planned_path: str) -> Path:
    candidate = (repo_root / planned_path).resolve()
    if repo_root not in candidate.parents and candidate != repo_root:
        raise ValueError("planned ADR path escapes the repository root")
    return candidate


def materialize(
    snapshot_or_path: Any,
    repo_root: Path | None = None,
    *,
    implementation: bool = False,
    issue_number: int | None = None,
) -> dict[str, Any]:
    try:
        snapshot = _load_snapshot(snapshot_or_path)
    except (OSError, TypeError, json.JSONDecodeError) as error:
        return {
            "status": "hold",
            "action": "report_hold",
            "risk_level": "unknown",
            "contract_id": issue_planning.contract_id({"invalid_snapshot": str(error)}),
            "errors": [f"snapshot could not be read as JSON: {error}"],
        }

    if isinstance(snapshot, dict) and issue_number is not None:
        existing_issue_number = snapshot.get("issue_number")
        if existing_issue_number is not None and existing_issue_number != issue_number:
            return {
                "status": "hold",
                "action": "report_hold",
                "risk_level": "unknown",
                "contract_id": issue_planning.contract_id(snapshot),
                "errors": ["CLI issue number must match snapshot issue_number"],
            }
        snapshot = {**snapshot, "issue_number": issue_number}

    resolved_repo_root = _repo_root(repo_root)
    risk_level, errors = issue_planning.validate(snapshot)
    if errors:
        return {
            "status": "hold",
            "action": "report_hold",
            "risk_level": risk_level,
            "contract_id": issue_planning.contract_id(snapshot),
            "errors": errors,
        }

    if not implementation:
        return {
            "status": "hold",
            "action": "require_implementation_context",
            "risk_level": risk_level,
            "contract_id": issue_planning.contract_id(snapshot),
            "errors": [
                "ADR materialization is allowed only when implementation begins"
            ],
        }

    adr = snapshot.get("adr")
    if not isinstance(adr, dict) or adr.get("required") is not True:
        return {
            "status": "hold",
            "action": "skip_no_adr",
            "risk_level": risk_level,
            "contract_id": issue_planning.contract_id(snapshot),
            "errors": ["adr.required must be true"],
        }

    planned_path, path_errors = issue_planning.resolve_adr_path(
        snapshot, require_final=True
    )
    if path_errors or planned_path is None:
        return {
            "status": "hold",
            "action": "require_final_adr_path",
            "risk_level": risk_level,
            "contract_id": issue_planning.contract_id(snapshot),
            "errors": path_errors or ["ADR path could not be resolved"],
        }

    try:
        target = _target_path(resolved_repo_root, planned_path)
    except ValueError as error:
        return {
            "status": "hold",
            "action": "report_hold",
            "risk_level": risk_level,
            "contract_id": issue_planning.contract_id(snapshot),
            "errors": [str(error)],
        }
    content = adr_contract.render_proposed_adr(snapshot, planned_path)

    if target.exists():
        current = target.read_text(encoding="utf-8")
        if current == content:
            return {
                "status": "pass",
                "action": "unchanged",
                "risk_level": risk_level,
                "contract_id": issue_planning.contract_id(snapshot),
                "path": str(target),
            }
        return {
            "status": "hold",
            "action": "refuse_overwrite",
            "risk_level": risk_level,
            "contract_id": issue_planning.contract_id(snapshot),
            "path": str(target),
            "errors": ["target ADR already exists with different content"],
        }

    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(content, encoding="utf-8")
    return {
        "status": "pass",
        "action": "created",
        "risk_level": risk_level,
        "contract_id": issue_planning.contract_id(snapshot),
        "path": str(target),
    }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("snapshot", type=Path, help="Issue contract JSON snapshot")
    parser.add_argument("--repo-root", type=Path, default=None, help="Repository root")
    parser.add_argument(
        "--issue-number",
        type=int,
        default=None,
        help="Actual GitHub Issue number used to finalize the ADR path",
    )
    parser.add_argument(
        "--implementation",
        action="store_true",
        help="Confirm that implementation has begun on the current worktree",
    )
    parser.add_argument(
        "--pretty", action="store_true", help="Pretty-print JSON output"
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    result = materialize(
        args.snapshot,
        args.repo_root,
        implementation=args.implementation,
        issue_number=args.issue_number,
    )
    print(
        json.dumps(
            result,
            ensure_ascii=False,
            indent=2 if args.pretty else None,
            sort_keys=True,
        )
    )
    return 0 if result["status"] == "pass" else 1


if __name__ == "__main__":
    raise SystemExit(main())
