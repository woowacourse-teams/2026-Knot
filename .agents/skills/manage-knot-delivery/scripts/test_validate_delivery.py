#!/usr/bin/env python3

import unittest

from validate_delivery import validate


CONFIG = {
    "title_pattern": r"^\[(BE|FE)\] \S.*",
    "area_labels": ["BE", "FE"],
    "type_labels": ["Feature", "Docs"],
    "worker_labels": ["루덴스"],
    "worker_by_assignee": {"poketopa": "루덴스"},
    "branch_pattern": r"^(?P<area>be|fe)/(?P<type>feature|bugfix|chore|docs|hotfix|refactor|release)/#(?P<issue>\d+)$",
    "branch_area_by_label": {"BE": "be", "FE": "fe"},
    "branch_type_by_label": {"Feature": "feature", "Docs": "docs"},
    "required_pr_sections": ["관련 이슈", "작업 내용"],
    "non_empty_pr_sections": ["작업 내용"],
    "issue_reference_section": "관련 이슈",
    "issue_reference_pattern": r"#\d+\b",
    "validate_draft_pull_requests": False,
}


def pull_request(
    body: str,
    *,
    title: str = "[BE] 협업 규칙 자동 검증",
    labels: list[str] | None = None,
    assignees: list[str] | None = None,
    draft: bool = False,
    head_ref: str = "be/docs/#4",
) -> dict[str, object]:
    return {
        "kind": "pull_request",
        "draft": draft,
        "head_ref": head_ref,
        "title": title,
        "body": body,
        "labels": labels if labels is not None else ["BE", "Docs", "루덴스"],
        "assignees": assignees if assignees is not None else ["poketopa"],
    }


class ValidateDeliveryTest(unittest.TestCase):
    def test_accepts_concise_pull_request(self) -> None:
        item = pull_request(
            "## 관련 이슈\n\n- #4\n\n"
            "## 작업 내용\n\n검증기를 간결한 템플릿에 맞췄습니다.\n\n"
            "### 참고 사항\n\n검증 결과는 리뷰 댓글을 참고합니다."
        )

        errors, warnings = validate(item, CONFIG)

        self.assertEqual([], errors)
        self.assertEqual([], warnings)

    def test_accepts_closing_keyword_and_extra_sections(self) -> None:
        item = pull_request(
            "## 작업 내용\n\n검증기를 수정했습니다.\n\n"
            "## 검증\n\n단위 테스트 통과\n\n"
            "## 관련 이슈\n\nCloses #4"
        )

        errors, _ = validate(item, CONFIG)

        self.assertEqual([], errors)

    def test_reports_only_objective_metadata_and_minimum_body_errors(self) -> None:
        item = pull_request(
            "## 관련 이슈\n\n번호 미정\n\n## 작업 내용\n\n<!-- 작성 예정 -->",
            title="[BE]  ",
            labels=["BE", "FE", "Docs", "루덴스"],
            assignees=[],
        )

        errors, _ = validate(item, CONFIG)

        self.assertTrue(any("제목" in error for error in errors))
        self.assertTrue(any("담당 영역 Label" in error for error in errors))
        self.assertTrue(any("Assignee" in error for error in errors))
        self.assertTrue(any("작업 내용을 작성" in error for error in errors))
        self.assertTrue(any("관련 이슈" in error for error in errors))

    def test_missing_optional_sections_does_not_fail(self) -> None:
        item = pull_request("## 관련 이슈\n\n#4\n\n## 작업 내용\n\n문서 수정")

        errors, _ = validate(item, CONFIG)

        self.assertEqual([], errors)

    def test_issue_number_outside_related_issue_section_does_not_pass(self) -> None:
        item = pull_request(
            "## 관련 이슈\n\n번호 미정\n\n## 작업 내용\n\nIssue #4와 관련된 문서 수정"
        )

        errors, _ = validate(item, CONFIG)

        self.assertTrue(any("관련 이슈" in error for error in errors))

    def test_unknown_assignee_is_warning_not_error(self) -> None:
        item = pull_request(
            "## 관련 이슈\n\n#4\n\n## 작업 내용\n\n문서 수정",
            assignees=["poketopa", "new-member"],
        )

        errors, warnings = validate(item, CONFIG)

        self.assertEqual([], errors)
        self.assertTrue(any("new-member" in warning for warning in warnings))

    def test_branch_segments_must_match_labels_and_related_issue(self) -> None:
        item = pull_request(
            "## 관련 이슈\n\n#4\n\n## 작업 내용\n\n문서 수정",
            head_ref="fe/feature/#99",
        )

        errors, _ = validate(item, CONFIG)

        self.assertTrue(any("area" in error for error in errors))
        self.assertTrue(any("type" in error for error in errors))
        self.assertTrue(any("Issue 번호" in error for error in errors))

    def test_branch_format_is_required(self) -> None:
        item = pull_request(
            "## 관련 이슈\n\n#4\n\n## 작업 내용\n\n문서 수정",
            head_ref="agent/gov-001-collaboration-guidelines",
        )

        errors, _ = validate(item, CONFIG)

        self.assertTrue(any("<area>/<type>/#<issue-number>" in error for error in errors))

    def test_draft_pull_request_is_not_blocked(self) -> None:
        item = pull_request(
            "",
            title="초안",
            labels=[],
            assignees=[],
            draft=True,
        )

        errors, warnings = validate(item, CONFIG)

        self.assertEqual([], errors)
        self.assertTrue(any("Draft PR" in warning for warning in warnings))


if __name__ == "__main__":
    unittest.main()
