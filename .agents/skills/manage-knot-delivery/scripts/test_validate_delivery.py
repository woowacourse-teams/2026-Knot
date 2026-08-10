#!/usr/bin/env python3

import unittest

from validate_delivery import validate


CONFIG = {
    "title_pattern": r"^\[(BE|FE)\] .+",
    "area_labels": ["BE", "FE"],
    "type_labels": ["Feature", "Docs"],
    "worker_labels": ["루덴스"],
    "worker_by_assignee": {"poketopa": "루덴스"},
    "required_pr_sections": [
        "관련 이슈",
        "변경 이유",
        "작업 내용",
        "영향 범위",
        "검증",
        "ADR / 명세",
        "리뷰 요청사항",
    ],
}


class ValidateDeliveryTest(unittest.TestCase):
    def test_valid_pull_request(self) -> None:
        body = "\n\n".join(
            [
                "## 관련 이슈\n\nCloses #4",
                "## 변경 이유\n\n협업 규칙을 자동 검증합니다.",
                "## 작업 내용\n\n검증기를 추가했습니다.",
                "## 영향 범위\n\n문서와 GitHub Actions만 변경합니다.",
                "## 검증\n\nunittest 통과",
                "## ADR / 명세\n\n불필요: 협업 자동화입니다.",
                "## 리뷰 요청사항\n\n설정과 문서의 일치 여부",
            ]
        )
        item = {
            "kind": "pull_request",
            "title": "[BE] 협업 규칙 자동 검증",
            "body": body,
            "labels": ["BE", "Docs", "루덴스"],
            "assignees": ["poketopa"],
        }

        self.assertEqual([], validate(item, CONFIG))

    def test_reports_metadata_and_body_errors(self) -> None:
        item = {
            "kind": "pull_request",
            "title": "협업 규칙 자동 검증",
            "body": "## 관련 이슈\n\n#4",
            "labels": ["BE", "FE", "Docs", "루덴스"],
            "assignees": [],
        }

        errors = validate(item, CONFIG)

        self.assertTrue(any("제목" in error for error in errors))
        self.assertTrue(any("담당 영역 Label" in error for error in errors))
        self.assertTrue(any("Assignee" in error for error in errors))
        self.assertTrue(any("Closes #번호" in error for error in errors))
        self.assertTrue(any("변경 이유" in error for error in errors))


if __name__ == "__main__":
    unittest.main()
