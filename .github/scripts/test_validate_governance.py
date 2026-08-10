#!/usr/bin/env python3

import unittest

from validate_governance import validate


CONFIG = {
    "title_pattern": r"^\[(BE|FE)\] \S.*",
    "branch_pattern": r"^(?P<area>be|fe)/(?P<type>feature|docs)/#(?P<issue>\d+)$",
    "required_pr_sections": ["관련 이슈", "작업 내용"],
    "non_empty_pr_sections": ["작업 내용"],
    "issue_reference_section": "관련 이슈",
    "issue_reference_pattern": r"#\d+\b",
}


def pull_request(
    body: str,
    *,
    title: str = "[BE] 협업 규칙 자동 검증",
    head_ref: str = "be/docs/#4",
) -> dict[str, object]:
    return {
        "title": title,
        "body": body,
        "head_ref": head_ref,
    }


class ValidateGovernanceTest(unittest.TestCase):
    def test_accepts_concise_pull_request(self) -> None:
        item = pull_request(
            "## 관련 이슈\n\n- #4\n\n"
            "## 작업 내용\n\n검증기를 간결한 템플릿에 맞췄습니다.\n\n"
            "### 참고 사항\n\n검증 결과는 리뷰 댓글을 참고합니다."
        )

        self.assertEqual([], validate(item, CONFIG))

    def test_accepts_closing_keyword_and_extra_sections(self) -> None:
        item = pull_request(
            "## 작업 내용\n\n검증기를 수정했습니다.\n\n"
            "## 검증\n\n단위 테스트 통과\n\n"
            "## 관련 이슈\n\nCloses #4"
        )

        self.assertEqual([], validate(item, CONFIG))

    def test_reports_title_and_minimum_body_errors(self) -> None:
        item = pull_request(
            "## 관련 이슈\n\n번호 미정\n\n## 작업 내용\n\n<!-- 작성 예정 -->",
            title="[BE]  ",
        )

        errors = validate(item, CONFIG)

        self.assertTrue(any("제목" in error for error in errors))
        self.assertTrue(any("작업 내용을 작성" in error for error in errors))
        self.assertTrue(any("관련 이슈" in error for error in errors))

    def test_missing_optional_sections_does_not_fail(self) -> None:
        item = pull_request("## 관련 이슈\n\n#4\n\n## 작업 내용\n\n문서 수정")

        self.assertEqual([], validate(item, CONFIG))

    def test_issue_number_outside_related_issue_section_does_not_pass(self) -> None:
        item = pull_request(
            "## 관련 이슈\n\n번호 미정\n\n## 작업 내용\n\nIssue #4와 관련된 문서 수정"
        )

        errors = validate(item, CONFIG)

        self.assertTrue(any("관련 이슈" in error for error in errors))

    def test_branch_area_and_issue_must_match_title_and_related_issue(self) -> None:
        item = pull_request(
            "## 관련 이슈\n\n#4\n\n## 작업 내용\n\n문서 수정",
            head_ref="fe/feature/#99",
        )

        errors = validate(item, CONFIG)

        self.assertTrue(any("area" in error for error in errors))
        self.assertTrue(any("Issue 번호" in error for error in errors))

    def test_labels_are_not_required(self) -> None:
        item = pull_request("## 관련 이슈\n\n#4\n\n## 작업 내용\n\n문서 수정")
        item["labels"] = []

        self.assertEqual([], validate(item, CONFIG))

    def test_branch_format_is_required(self) -> None:
        item = pull_request(
            "## 관련 이슈\n\n#4\n\n## 작업 내용\n\n문서 수정",
            head_ref="agent/gov-001-collaboration-guidelines",
        )

        errors = validate(item, CONFIG)

        self.assertTrue(any("<area>/<type>/#<issue-number>" in error for error in errors))


if __name__ == "__main__":
    unittest.main()
