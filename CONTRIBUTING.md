# Knot 기여 가이드

이 문서는 Issue를 선택한 시점부터 PR을 병합할 때까지 모든 팀원이 따르는 기본 절차입니다. 세부 상태 전이와 예외 처리는 [협업 흐름](docs/collaboration/workflow.md)을 따릅니다.

## 1. 작업 시작 전

1. 작업을 설명하는 GitHub Issue가 있는지 확인합니다.
2. [Definition of Ready](docs/collaboration/definition-of-ready-done.md#definition-of-ready)를 만족하는지 확인합니다.
3. 선행 Issue와 외부 의존성이 해결됐는지 확인합니다.
4. 기술 결정이 필요하면 구현 전에 ADR 필요 여부를 Issue에 기록합니다.
5. 담당 영역과 작업 유형 Label, Assignee를 지정합니다.

Ready 조건을 만족하지 않으면 구현을 시작하지 않고 부족한 결정이나 자료를 Issue에 기록합니다.

## 2. Issue와 Pull Request 제목·Label

Issue와 PR 제목은 담당 영역만 접두사로 표시합니다.

```text
[BE] Notion 페이지 조회 클라이언트 구현
[FE] 워크스페이스 연결 화면 구현
```

- 백엔드 작업은 `[BE]`, 프론트엔드 작업은 `[FE]`로 시작합니다.
- `Feature`, `Chore` 같은 작업 유형이나 작업자 이름을 제목에 추가하지 않습니다.
- Issue와 연결된 PR은 같은 담당 영역 접두사를 사용합니다.

Issue와 PR에는 아래 두 종류의 Label을 모두 지정합니다.

| 구분 | 선택 규칙 | 현재 Label |
| --- | --- | --- |
| 담당 영역 | 정확히 1개 | `BE`, `FE` |
| 작업 유형 | 정확히 1개 | `Feature`, `BugFix`, `Chore`, `Docs`, `Hotfix`, `Refactor`, `Release` |

현재 저장소의 기능 개발 Label 명칭은 `Feat`가 아니라 `Feature`입니다. Label 명칭을 변경하기 전까지 Issue와 PR에는 `Feature`를 사용합니다.

## 3. 브랜치

- `main`에서 최신 변경을 받은 뒤 작업 브랜치를 만듭니다.
- 브랜치 또는 Draft PR을 만든 뒤 Project 상태를 `In Progress`로 변경합니다.
- 하나의 브랜치는 하나의 Issue 결과만 다룹니다.
- 모든 작업 브랜치는 `<area>/<type>/#<issue-number>` 형식을 사용합니다.
- `area`는 Issue와 PR의 담당 영역 Label에 따라 `be` 또는 `fe`를 사용합니다.
- `type`은 작업 유형 Label을 소문자로 변환한 `feature`, `bugfix`, `chore`, `docs`, `hotfix`, `refactor`, `release` 중 하나를 사용합니다.
- 마지막 구간은 연결된 Issue 번호 앞에 `#`을 붙이며 작업 요약을 추가하지 않습니다.
- 셸에서 `#`이 주석으로 해석되지 않도록 브랜치 이름을 따옴표로 감싸 사용합니다.

예시:

```text
be/chore/#2
fe/feature/#15
be/docs/#4
```

```bash
git switch -c 'be/chore/#2'
```

## 4. 구현과 커밋

- Issue의 완료 조건을 기준으로 구현합니다.
- 범위 밖 요구사항은 현재 변경에 섞지 않고 별도 Issue로 분리합니다.
- 커밋은 한 가지 의도를 설명할 수 있는 크기로 작성합니다.
- 비밀값, 개인 환경 설정, 로컬 절대 경로를 커밋하지 않습니다.
- API가 변경되면 Swagger UI에서 확인할 수 있는 OpenAPI 문서도 함께 갱신합니다.
- 중요한 기술 선택이 확정되면 관련 ADR을 함께 추가하거나 갱신합니다.

## 5. Pull Request

1. 가능한 한 일찍 Draft PR을 열어 구현 맥락을 공유합니다.
2. Draft 상태에서는 Project 상태를 `In Progress`로 유지합니다.
3. 리뷰 가능한 상태가 되면 Draft를 해제하고 `In Review`로 변경합니다.
4. 제목과 담당 영역·작업 유형 Label, Assignee를 Issue와 일치시킵니다.
5. PR 본문의 `관련 이슈`에 `#<issue-number>`를 작성합니다. 병합 시 Issue를 자동 종료하려면 `Closes #<issue-number>`를 사용합니다.
6. PR 템플릿의 `관련 이슈`와 `작업 내용`을 작성하고, 검증 결과·영향 범위·관련 ADR처럼 리뷰에 필요한 맥락은 `작업 내용` 또는 `참고 사항`에 기록합니다.
7. CI, 필수 리뷰, 미해결 대화 조건을 모두 충족한 뒤 병합합니다.

직접 `main`에 push하거나 리뷰되지 않은 변경을 병합하지 않습니다.

## 6. 완료와 인계

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

## 자동 검증

- 제목, Label, 브랜치, PR 본문 규칙의 단일 설정은 [`.github/knot-conventions.yml`](.github/knot-conventions.yml)입니다.
- 설정 파일은 별도 YAML 패키지 없이 검증할 수 있도록 JSON 문법과 호환되는 YAML로 유지합니다.
- GitHub Actions의 `Governance` 검사는 PR 메타데이터가 설정과 일치하는지 확인합니다.
- 자동 검사는 제목 형식, Label 개수, 브랜치, PR의 최소 구조와 Issue 번호처럼 기계적으로 판정 가능한 규칙만 차단합니다. 설명의 충분성, 영향 범위, ADR 필요 여부는 리뷰와 Ready/Done 확인에서 판단합니다.
- 자동화 결과가 이 문서와 충돌하면 설정만 임의로 고치지 않고 같은 PR에서 문서와 설정을 함께 변경합니다.
