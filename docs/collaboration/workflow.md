# Knot 협업 흐름

Knot는 Scrumban으로 작업 흐름을 관리하고 GitHub Flow로 코드를 통합합니다. 실시간 작업 상태는 GitHub에 한 번만 기록하고, Notion은 제품 맥락과 문서 탐색을 위한 포털로 사용합니다.

## 도구별 Source of Truth

| 정보 | Source of Truth | 다른 도구에 남기는 내용 |
| --- | --- | --- |
| 작업 상태, 담당자, 완료 조건, 차단 사유 | GitHub Issue / Project | Notion에는 Project 또는 Milestone 링크만 둡니다. |
| 제품 목표, 사용자 흐름, 확정 기능 명세 | Notion | Issue에는 구현에 필요한 원문 링크와 확인일을 둡니다. |
| 중요한 기술 선택과 트레이드오프 | 저장소 ADR | Issue와 PR에서 ADR을 링크합니다. |
| 코드와 실행 가능한 동작 | Git 저장소와 테스트 | 관련 Issue, PR, ADR을 연결합니다. |
| API 요청·응답 계약 | Swagger UI / OpenAPI | Notion에는 API 문서 진입 링크만 둡니다. |

같은 상태나 결정을 여러 도구에 복사하지 않습니다. 서로 다른 기록이 충돌하면 아래 순서로 처리합니다.

1. 작업 상태는 GitHub를 따릅니다.
2. 제품 동작은 Notion의 확정 명세를 확인합니다.
3. 기술 선택은 최신 Accepted ADR을 확인합니다.
4. 어느 기준으로도 해결되지 않으면 구현을 멈추고 결정이 필요한 내용을 Issue에 기록합니다.

## Issue와 Pull Request 메타데이터

- 제목은 담당 영역에 따라 `[BE]` 또는 `[FE]`로 시작합니다.
- 담당 영역 Label은 `BE`와 `FE` 중 정확히 하나를 지정합니다.
- 작업 유형 Label은 `Feature`, `BugFix`, `Chore`, `Docs`, `Hotfix`, `Refactor`, `Release` 중 정확히 하나를 지정합니다.
- Issue와 연결된 PR은 담당 영역, 작업 유형 Label과 Assignee를 동일하게 유지합니다.

제목과 Label의 자세한 예시는 [`CONTRIBUTING.md`](../../CONTRIBUTING.md#2-issue와-pull-request-제목label)를 따릅니다.

## Scrumban 상태

| 상태 | 의미 | 진입 조건 | 다음 상태 |
| --- | --- | --- | --- |
| `Backlog` | 필요성은 기록됐지만 아직 시작할 수 없는 작업 | 목표 또는 아이디어가 Issue로 등록됨 | `Ready`, 종료 |
| `Ready` | 바로 시작할 수 있는 작업 | Definition of Ready 충족 | `In Progress` |
| `In Progress` | 담당자가 현재 수행 중인 작업 | 담당자 지정, 브랜치 또는 Draft PR 존재 | `In Review`, `Blocked` |
| `In Review` | 구현이 끝나 리뷰와 검증을 기다리는 작업 | Draft 해제, 자체 검증 완료 | `In Progress`, `Blocked`, `Done` |
| `Blocked` | 외부 결정이나 의존성 때문에 진행할 수 없는 작업 | 차단 사유, 해제 조건, 다음 확인 담당자 기록 | 이전 상태 |
| `Done` | 완료 조건을 충족하고 기본 브랜치에 반영된 작업 | Definition of Done 충족, PR 병합 | 없음 |

Draft PR은 조기 공유 수단이며 `In Review`를 의미하지 않습니다. 리뷰 요청이 가능한 상태로 Draft를 해제했을 때 `In Review`로 이동합니다.

## WIP 제한

- 초기 WIP 제한은 담당자 한 명당 주 작업 `In Progress` 1개입니다.
- 리뷰 대응은 별도 WIP로 계산하지 않지만 오래 방치하지 않습니다.
- 긴급 장애 등으로 제한을 넘기면 기존 작업을 `Blocked` 또는 `Backlog`로 옮기고 이유를 기록합니다.
- 새 일을 시작하기보다 `In Review`와 `Blocked` 항목을 먼저 해소합니다.

## GitHub Flow

1. `Ready` Issue를 선택하고 담당자를 지정합니다.
2. `main`을 기준으로 Issue 전용 브랜치를 생성합니다.
3. 브랜치 또는 Draft PR이 생기면 Project 상태를 `In Progress`로 변경합니다.
4. 작은 커밋으로 변경하고 테스트를 반복합니다.
5. 구현 맥락 공유가 필요하면 Draft PR을 일찍 생성합니다.
6. 완료 조건과 자체 검증을 충족하면 Draft를 해제하고 리뷰를 요청합니다.
7. CI, 필수 리뷰, 미해결 대화 조건을 모두 충족합니다.
8. PR을 병합하고 Issue와 Project 상태가 `Done`인지 확인합니다.
9. 다음 `Ready` Issue를 가져옵니다.

상세 기여 절차는 [`CONTRIBUTING.md`](../../CONTRIBUTING.md)를 따릅니다.

## 운영 주기

Scrumban은 고정 Sprint 대신 흐름을 유지합니다.

- 수시: `Blocked`와 리뷰 대기 항목을 우선 해소합니다.
- 주 1회: Backlog를 검토해 WIP만큼 `Ready` 큐를 보충합니다.
- 마일스톤 중간: 완료율, 남은 위험, 의존성을 확인합니다.
- 마일스톤 종료: 미완료 Issue의 이동 또는 종료를 결정하고 간단히 회고합니다.

우선순위와 마일스톤 범위는 자동화하지 않습니다. 팀이 합의한 뒤 GitHub에 반영합니다.

## ADR이 필요한 경우

다음 중 하나에 해당하면 구현 전에 ADR 필요 여부를 검토합니다.

- 데이터 모델이나 외부 API 연동 방식이 장기간 영향을 줍니다.
- 보안, 개인정보, tenant 격리, 트랜잭션 경계가 달라집니다.
- 여러 대안 중 하나를 선택했고 되돌리는 비용이 큽니다.
- 기존 Accepted ADR과 다른 방향이 필요합니다.
- 이후 팀원이 선택 이유를 다시 질문할 가능성이 큽니다.

ADR 체계와 템플릿은 후속 ADR-as-Code 초기 세팅 Issue에서 추가합니다. 그전까지 `ADR 필요` 또는 `검토 중`인 Issue는 `Ready`로 이동하거나 구현을 시작하지 않습니다. ADR 체계가 준비된 뒤에는 필요한 결정이 Accepted ADR로 연결되어야 `Ready`로 이동할 수 있습니다.

## 자동화 경계

- [`.github/knot-conventions.yml`](../../.github/knot-conventions.yml)은 제목, Label, 브랜치, PR 필수 본문 항목의 단일 설정입니다.
- `Governance` GitHub Actions는 규칙 위반을 차단하지만 우선순위, 마일스톤 범위, ADR 필요 여부 같은 팀 판단을 대신하지 않습니다.

## Notion Portal 원칙

- Portal은 GitHub Project, Milestone, ADR 목록, BE 위키, Swagger UI의 진입 링크를 제공합니다.
- Issue 상태, 담당자, 완료율을 Notion에 수동으로 복사하지 않습니다.
- 제품 명세에는 책임자, 상태, 마지막 확인일을 표시합니다.
- 구현 Issue에는 사용한 Notion 원문 링크와 마지막 확인일을 기록합니다.

Notion Portal의 실제 구성은 후속 Notion Portal 초기 세팅 Issue에서 진행합니다.
