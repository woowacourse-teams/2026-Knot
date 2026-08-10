# Knot 기여 가이드

이 문서는 Issue를 선택한 시점부터 PR을 병합할 때까지 모든 팀원이 따르는 기본 절차입니다. 세부 상태 전이와 예외 처리는 [협업 흐름](docs/collaboration/workflow.md)을 따릅니다.

## 1. 작업 시작 전

1. 작업을 설명하는 GitHub Issue가 있는지 확인합니다.
2. [Definition of Ready](docs/collaboration/definition-of-ready-done.md#definition-of-ready)를 만족하는지 확인합니다.
3. 선행 Issue와 외부 의존성이 해결됐는지 확인합니다.
4. 기술 결정이 필요하면 구현 전에 ADR 필요 여부를 Issue에 기록합니다.
5. 담당자를 지정하고 Project 상태를 `In Progress`로 변경합니다.

Ready 조건을 만족하지 않으면 구현을 시작하지 않고 부족한 결정이나 자료를 Issue에 기록합니다.

## 2. 브랜치

- `main`에서 최신 변경을 받은 뒤 작업 브랜치를 만듭니다.
- 하나의 브랜치는 하나의 Issue 결과만 다룹니다.
- 사람이 만드는 브랜치는 `<area>/<type>/<issue-number>-<summary>` 형식을 권장합니다.
- 자동화 도구가 만드는 브랜치는 `agent/<issue-key>-<summary>` 형식을 사용할 수 있습니다.

예시:

```text
be/feature/42-import-notion-page
be/refactor/57-block-parser
docs/chore/4-collaboration-guidelines
agent/gov-001-collaboration-guidelines
```

## 3. 구현과 커밋

- Issue의 완료 조건을 기준으로 구현합니다.
- 범위 밖 요구사항은 현재 변경에 섞지 않고 별도 Issue로 분리합니다.
- 커밋은 한 가지 의도를 설명할 수 있는 크기로 작성합니다.
- 비밀값, 개인 환경 설정, 로컬 절대 경로를 커밋하지 않습니다.
- API가 변경되면 Swagger UI에서 확인할 수 있는 OpenAPI 문서도 함께 갱신합니다.
- 중요한 기술 선택이 확정되면 관련 ADR을 함께 추가하거나 갱신합니다.

## 4. Pull Request

1. 가능한 한 일찍 Draft PR을 열어 구현 맥락을 공유합니다.
2. Draft 상태에서는 Project 상태를 `In Progress`로 유지합니다.
3. 리뷰 가능한 상태가 되면 Draft를 해제하고 `In Review`로 변경합니다.
4. PR 본문에 `Closes #<issue-number>`를 작성합니다.
5. 변경 이유, 검증 결과, API·DB 영향, 관련 ADR을 기록합니다.
6. CI, 필수 리뷰, 미해결 대화 조건을 모두 충족한 뒤 병합합니다.

직접 `main`에 push하거나 리뷰되지 않은 변경을 병합하지 않습니다.

## 5. 완료와 인계

- 병합 전에 [Definition of Done](docs/collaboration/definition-of-ready-done.md#definition-of-done)을 확인합니다.
- 병합 후 연결된 Issue와 Project 상태가 `Done`인지 확인합니다.
- 작업이 중단되거나 담당자가 변경되면 [Context Handoff](docs/collaboration/context-handoff.md)를 Issue 또는 PR에 남깁니다.

## 문서 책임

| 알고 싶은 내용 | 기준 위치 |
| --- | --- |
| 현재 작업 상태와 완료 조건 | GitHub Issue / Project |
| 제품 의도와 상세 기획 | 팀 Notion |
| 기술 결정과 트레이드오프 | 저장소 `docs/adr` |
| API 계약 | 실행 중인 Swagger UI / OpenAPI |
| 협업 절차 | 이 문서와 `docs/collaboration` |

내용이 충돌하면 임의로 해석하지 말고 Issue에 충돌 지점과 필요한 결정을 기록합니다.
