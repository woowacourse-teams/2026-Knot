#!/usr/bin/env python3
"""Validate Knot Issue and pull request metadata against repository conventions."""

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


def _names(values: list[Any], key: str) -> list[str]:
    result: list[str] = []
    for value in values:
        if isinstance(value, str):
            result.append(value)
        elif isinstance(value, dict) and isinstance(value.get(key), str):
            result.append(value[key])
    return result


def normalize_payload(payload: dict[str, Any]) -> dict[str, Any]:
    if "pull_request" in payload:
        item = payload["pull_request"]
        kind = "pull_request"
    elif "issue" in payload:
        item = payload["issue"]
        kind = "issue"
    elif payload.get("kind") in {"issue", "pull_request"}:
        item = payload
        kind = payload["kind"]
    else:
        raise ValueError("Issue 또는 pull_request payload가 아닙니다.")

    return {
        "kind": kind,
        "title": item.get("title", ""),
        "body": item.get("body") or "",
        "labels": _names(item.get("labels") or [], "name"),
        "assignees": _names(item.get("assignees") or [], "login"),
    }


def load_from_github(repo: str, kind: str, number: int) -> dict[str, Any]:
    resource = "pr" if kind == "pull_request" else "issue"
    command = [
        "gh",
        resource,
        "view",
        str(number),
        "--repo",
        repo,
        "--json",
        "title,body,labels,assignees",
    ]
    completed = subprocess.run(command, check=False, capture_output=True, text=True)
    if completed.returncode != 0:
        message = completed.stderr.strip() or completed.stdout.strip()
        raise ValueError(f"GitHub {resource} 조회 실패: {message}")
    item = json.loads(completed.stdout)
    item["kind"] = kind
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
    labels = set(item["labels"])
    assignees = set(item["assignees"])

    title_match = re.fullmatch(config["title_pattern"], title)
    if not title_match:
        errors.append("제목은 [BE] 또는 [FE]로 시작하고 뒤에 작업명을 포함해야 합니다.")

    area_labels = labels.intersection(config["area_labels"])
    if len(area_labels) != 1:
        errors.append("담당 영역 Label은 BE와 FE 중 정확히 하나여야 합니다.")
    elif title_match and title_match.group(1) not in area_labels:
        errors.append("제목의 담당 영역과 담당 영역 Label이 일치해야 합니다.")

    type_labels = labels.intersection(config["type_labels"])
    if len(type_labels) != 1:
        errors.append("작업 유형 Label은 정확히 하나여야 합니다.")

    worker_labels = labels.intersection(config["worker_labels"])
    if not worker_labels:
        errors.append("작업자 Label을 하나 이상 지정해야 합니다.")

    worker_by_assignee = config["worker_by_assignee"]
    assignee_by_worker = {worker: login for login, worker in worker_by_assignee.items()}
    for assignee in sorted(assignees):
        expected_worker = worker_by_assignee.get(assignee)
        if expected_worker is None:
            errors.append(f"Assignee @{assignee}의 작업자 Label 매핑이 설정에 없습니다.")
        elif expected_worker not in worker_labels:
            errors.append(f"Assignee @{assignee}에 대응하는 작업자 Label '{expected_worker}'가 없습니다.")
    for worker in sorted(worker_labels):
        expected_assignee = assignee_by_worker.get(worker)
        if expected_assignee is None:
            errors.append(f"작업자 Label '{worker}'의 Assignee 매핑이 설정에 없습니다.")
        elif expected_assignee not in assignees:
            errors.append(f"작업자 Label '{worker}'에 대응하는 Assignee @{expected_assignee}가 없습니다.")

    if item["kind"] == "pull_request":
        body = item["body"]
        matches = _section_matches(body)
        headings = [match.group(1).strip() for match in matches]
        required = config["required_pr_sections"]
        positions: list[int] = []
        for section in required:
            if section not in headings:
                errors.append(f"PR 본문에 '## {section}' 섹션이 없습니다.")
                continue
            index = headings.index(section)
            positions.append(index)
            if not _section_content(body, matches, index):
                errors.append(f"PR 본문의 '## {section}' 섹션이 비어 있습니다.")
        if len(positions) == len(required) and positions != sorted(positions):
            errors.append("PR 본문의 필수 섹션 순서가 컨벤션과 다릅니다.")
        if not re.search(
            r"(?im)\b(?:close[sd]?|fix(?:e[sd])?|resolve[sd]?)\s+#\d+\b", body
        ):
            errors.append("PR 본문에 'Closes #번호' 형식으로 Issue를 연결해야 합니다.")

    return errors


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config", type=Path, default=DEFAULT_CONFIG)
    source = parser.add_mutually_exclusive_group(required=True)
    source.add_argument("--payload", type=Path, help="GitHub event 또는 정규화 JSON 파일")
    source.add_argument("--issue", type=int, help="검증할 Issue 번호")
    source.add_argument("--pr", type=int, help="검증할 pull request 번호")
    parser.add_argument("--repo", help="OWNER/REPO. --issue 또는 --pr 사용 시 필수")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    try:
        config = load_config(args.config)
        if args.payload:
            payload = json.loads(args.payload.read_text(encoding="utf-8"))
            item = normalize_payload(payload)
        elif not args.repo:
            raise ValueError("--issue 또는 --pr 사용 시 --repo가 필요합니다.")
        elif args.issue:
            item = load_from_github(args.repo, "issue", args.issue)
        else:
            item = load_from_github(args.repo, "pull_request", args.pr)
    except (OSError, ValueError, json.JSONDecodeError) as error:
        print(f"::error::{error}")
        return 2

    errors = validate(item, config)
    if errors:
        for error in errors:
            print(f"::error::{error}")
        return 1

    print(f"Knot {item['kind']} 컨벤션 검증을 통과했습니다.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
