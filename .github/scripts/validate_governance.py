#!/usr/bin/env python3
"""Validate pull request metadata against Knot repository conventions."""

from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
from pathlib import Path
from typing import Any


DEFAULT_CONFIG = Path(".github/knot-conventions.yml")


def load_config(path: Path) -> dict[str, Any]:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError as error:
        raise ValueError(f"설정 파일을 찾을 수 없습니다: {path}") from error
    except json.JSONDecodeError as error:
        raise ValueError(f"설정 파일이 JSON 호환 YAML 형식이 아닙니다: {error}") from error


def normalize_payload(payload: dict[str, Any]) -> dict[str, Any]:
    if "pull_request" in payload:
        item = payload["pull_request"]
    elif payload.get("kind") == "pull_request":
        item = payload
    else:
        raise ValueError("pull_request payload가 아닙니다.")

    head = item.get("head") or {}
    head_ref = (
        head.get("ref", "") if isinstance(head, dict) else ""
    ) or item.get("headRefName", item.get("head_ref", ""))

    return {
        "title": item.get("title", ""),
        "body": item.get("body") or "",
        "head_ref": head_ref,
    }


def load_from_github(repo: str, number: int) -> dict[str, Any]:
    command = [
        "gh",
        "pr",
        "view",
        str(number),
        "--repo",
        repo,
        "--json",
        "title,body,headRefName",
    ]
    completed = subprocess.run(command, check=False, capture_output=True, text=True)
    if completed.returncode != 0:
        message = completed.stderr.strip() or completed.stdout.strip()
        raise ValueError(f"GitHub PR 조회 실패: {message}")
    item = json.loads(completed.stdout)
    item["kind"] = "pull_request"
    return normalize_payload(item)


def _section_matches(body: str) -> list[re.Match[str]]:
    return list(re.finditer(r"(?m)^##\s+(.+?)\s*$", body))


def _section_content(body: str, matches: list[re.Match[str]], index: int) -> str:
    start = matches[index].end()
    end = matches[index + 1].start() if index + 1 < len(matches) else len(body)
    content = re.sub(r"<!--.*?-->", "", body[start:end], flags=re.DOTALL)
    return content.strip()


def validate(item: dict[str, Any], config: dict[str, Any]) -> list[str]:
    errors: list[str] = []
    title = item["title"]

    title_match = re.fullmatch(config["title_pattern"], title)
    if not title_match:
        errors.append("제목은 [BE] 또는 [FE]로 시작하고 뒤에 작업명을 포함해야 합니다.")

    body = item["body"]
    matches = _section_matches(body)
    headings = [match.group(1).strip() for match in matches]
    for section in config["required_pr_sections"]:
        if section not in headings:
            errors.append(f"PR 본문에 '## {section}' 섹션이 없습니다.")

    for section in config.get("non_empty_pr_sections", []):
        if section not in headings:
            continue
        index = headings.index(section)
        if not _section_content(body, matches, index):
            errors.append(f"PR 본문의 '## {section}' 섹션에 작업 내용을 작성해야 합니다.")

    issue_section = config["issue_reference_section"]
    issue_section_content = ""
    if issue_section in headings:
        issue_section_index = headings.index(issue_section)
        issue_section_content = _section_content(body, matches, issue_section_index)
        if not re.search(config["issue_reference_pattern"], issue_section_content):
            errors.append(
                f"PR 본문의 '## {issue_section}' 섹션에 '#번호' 형식으로 Issue를 연결해야 합니다."
            )

    head_ref = item.get("head_ref", "")
    branch_match = re.fullmatch(config["branch_pattern"], head_ref)
    if not branch_match:
        errors.append("브랜치는 '<area>/<type>/#<issue-number>' 형식이어야 합니다.")
    else:
        if title_match and branch_match.group("area") != title_match.group(1).lower():
            errors.append("브랜치의 area가 제목의 담당 영역과 일치해야 합니다.")
        branch_issue = branch_match.group("issue")
        if issue_section_content and not re.search(
            rf"#{re.escape(branch_issue)}\b", issue_section_content
        ):
            errors.append("브랜치의 Issue 번호가 관련 이슈와 일치해야 합니다.")

    return errors


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config", type=Path, default=DEFAULT_CONFIG)
    source = parser.add_mutually_exclusive_group(required=True)
    source.add_argument("--payload", type=Path, help="GitHub pull_request event JSON")
    source.add_argument("--pr", type=int, help="검증할 pull request 번호")
    parser.add_argument("--repo", help="OWNER/REPO. --pr 사용 시 필수")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    try:
        config = load_config(args.config)
        if args.payload:
            payload = json.loads(args.payload.read_text(encoding="utf-8"))
            item = normalize_payload(payload)
        elif not args.repo:
            raise ValueError("--pr 사용 시 --repo가 필요합니다.")
        else:
            item = load_from_github(args.repo, args.pr)
    except (OSError, ValueError, json.JSONDecodeError) as error:
        print(f"::error::{error}")
        return 2

    errors = validate(item, config)
    if errors:
        for error in errors:
            print(f"::error::{error}")
        return 1

    print("Knot pull request 컨벤션 검증을 통과했습니다.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
