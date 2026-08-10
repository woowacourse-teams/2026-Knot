---
name: manage-knot-delivery
description: Knot 저장소에서 GitHub Issue 또는 Pull Request를 생성·수정·검토하거나 제목, Label, Assignee, 마일스톤, 연결 이슈, PR 본문 컨벤션을 검증할 때 사용한다. 협업 상태와 작업 인계를 정리하는 요청에도 사용한다. 백엔드 기능 구현 자체나 일반적인 Git 사용법 설명에는 사용하지 않는다.
---

# Manage Knot Delivery

Knot의 협업 규칙을 저장소 문서와 단일 설정에서 읽어 Issue와 PR에 일관되게 적용한다. 규칙을 추측하거나 문서에 중복 정의하지 않는다.

## 기준 읽기

1. `git rev-parse --show-toplevel`로 저장소 루트를 확인한다.
2. 다음 파일을 순서대로 읽는다.
   - `.github/knot-conventions.yml`
   - `CONTRIBUTING.md`
   - `docs/collaboration/workflow.md`
   - 해당 작업의 Issue 또는 PR 템플릿
3. 상태 전이 또는 Ready/Done 판정이 포함되면 `docs/collaboration/definition-of-ready-done.md`도 읽는다.
4. 작업 인계가 포함되면 `docs/collaboration/context-handoff.md`를 읽는다.

## 작업 절차

1. `gh repo view --json nameWithOwner` 또는 연결된 GitHub 도구로 저장소를 확인한다.
2. 대상 Issue나 PR의 제목, 본문, Label, Assignee, 마일스톤, 연결 관계를 읽는다.
3. 사용자 요청과 Issue 범위에 맞는 변경만 준비한다.
4. 제목에는 `[BE]` 또는 `[FE]`만 접두사로 사용한다. 작업 유형과 작업자는 Label로 표현한다.
5. 담당 영역 Label과 작업 유형 Label을 각각 정확히 하나 지정한다.
6. 모든 Assignee에 대응하는 작업자 Label을 지정하고, 모든 작업자 Label에 대응하는 Assignee를 지정한다.
7. PR은 Issue와 영역·유형·작업자 메타데이터를 일치시키고 `Closes #번호`로 연결한다.
8. PR 본문은 설정 파일의 `required_pr_sections` 순서를 따르고 빈 섹션을 남기지 않는다.
9. 외부 상태를 변경하라는 요청이면 GitHub 연결 도구를 우선 사용하고, 지원되지 않을 때 인증된 `gh` CLI를 사용한다.
10. 생성 또는 수정 후 검증 스크립트를 실행하고 결과를 보고한다.

## 검증 명령

저장소 루트에서 실행한다.

```bash
python3 .agents/skills/manage-knot-delivery/scripts/validate_delivery.py --repo OWNER/REPO --issue 42
python3 .agents/skills/manage-knot-delivery/scripts/validate_delivery.py --repo OWNER/REPO --pr 43
```

검증 실패는 자동으로 우회하지 않는다. 메타데이터를 수정할 권한이 없거나 작업자 매핑이 없으면 정확한 누락 항목을 보고한다.

## 판단 경계

- 우선순위, 마일스톤 편입, ADR 필요 여부는 자동으로 결정하지 않는다.
- 제품 정책이 불명확하면 Issue에 결정 필요 사항을 남기고 `Ready` 또는 구현 시작으로 진행하지 않는다.
- 규칙 변경 요청은 `.github/knot-conventions.yml`, 관련 문서, 템플릿, 검증기 테스트를 같은 PR에서 함께 갱신한다.
- 세부 실행 체크리스트는 [`references/delivery-checklist.md`](references/delivery-checklist.md)를 따른다.
