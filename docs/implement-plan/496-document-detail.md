# #496 문서 상세 조회 구현 계획

상태: API 구현·전체 Gradle 검증 완료, Persona 완료 인증 미통과 · 확인일: 2026-10-06 · 브랜치: `be/feature/#496`.
기준: 사용자 최신 API, 대화에서 확정한 제품 결정, Issue #496과 현재 코드. 사용자 결정과 팀 승인 여부는 구분한다. 아래 본문은 구현 전 계획이며 실제 진행 사항은 마지막 절에 기록한다.

## 1. 범위와 브랜치

[#496](https://github.com/woowacourse-teams/2026-Knot/issues/496)은 `GET /api/v1/workspaces/{workspaceId}/documents/{documentId}`를 완성하는 작업이다. 현재 체크아웃한 `be/feature/#496`에서 진행한다.

현재 Document·Transcript·DocumentGenerationJob·DocumentConfirmation 구현이 없어 상세 조회에 필요한 최소 저장 기반도 함께 만든다. 이번 완료 범위는 저장된 문서의 본문·출처·내 확인 상태·확인 집계를 실제 인증과 PostgreSQL을 거쳐 반환하는 것까지다.

문서 API 다섯 개는 `DocumentController implements DocumentApi`로 모으고, 이번에는 상세 GET 메서드만 작성한다. Job API 두 개는 이후 `DocumentGenerationJobController implements DocumentGenerationJobApi`에 둔다. 자동 생성 실행은 #501, 확인 처리는 #498, 원문 구간 조회는 #499에 연결한다.

구현량은 아직 산정하지 않았다. 저장 기반이 포함되므로 실제 변경량을 보고 PR 범위를 판단한다. 열린 PR #480·#483·#485의 읽기/저장 제약/업로드 완료 패턴은 확인했지만 현재 브랜치에 병합된 구현으로 취급하지 않는다. 업로드 완료가 STT와 문서 생성 완료를 뜻하지 않는다.

## 2. 구현할 흐름

로그인한 사용자가 문서를 선택하면 현재 Workspace 멤버 여부를 검사한 뒤 해당 Workspace의 문서와 확인 기록을 읽는다. 녹음 참여 여부는 문서 조회 권한의 조건이 아니다.

```text
GET /api/v1/workspaces/{workspaceId}/documents/{documentId}
  → 기존 Security: 로그인 검사
  → DocumentController: path ID와 인증된 memberId 전달
  → DocumentDetailService: 현재 Workspace 멤버 검사
  → DocumentDetailQuery: 문서·녹음·원문 연결·확인 집계 조회
  → 내 확인 상태 판정 및 DocumentDetailResult 구성
  → DocumentDetailResponse 변환
  → HTTP 200
```

| 조건 | 결과 |
| --- | --- |
| 로그인하지 않음 | 401 UNAUTHENTICATED |
| 현재 Workspace 멤버가 아님·탈퇴·Workspace 삭제 | 403 WORKSPACE_ACCESS_DENIED |
| 문서 없음 또는 다른 Workspace의 문서 | 404 DOCUMENT_NOT_FOUND |
| 숫자 경로의 형식 오류 | 기존 handler의 400 INVALID_PARAMETER |
| 현재 멤버이고 해당 Workspace 문서 존재 | 200, 상세 응답 |

예를 들어 생성 당시 대상 A·B·C 중 A가 확인했고 B는 활성 미확인, C는 미확인 탈퇴라면 집계는 confirmed=1/pending=1/excluded=1이다. 이후 가입한 D는 문서를 읽을 수 있지만 NOT_REQUIRED다. A는 CONFIRMED, B는 PENDING이다.

이 GET은 확인 기록이나 문서 status/archivedAt을 변경하지 않는다. 성공 문서는 다른 주제 Job의 실패·만료와 관계없이 즉시 조회한다. `recordingSessionId`는 응답 필드이며 path에는 workspaceId/documentId만 둔다. 예시 JSON의 누락된 쉼표와 `No해당` 오타는 명세 정리 시 수정한다.

## 3. 나올 코드와 메서드 초안

기존 `WorkspaceOwnershipTransferCandidateApi/Controller`의 Swagger·HTTP 경계, `WorkspaceOwnershipTransferCandidateQuery/QueryAdapter`의 다중 테이블 조회, `WorkspaceMemberRepository`의 활성 멤버 검사, `GlobalExceptionHandler`의 오류 처리를 재사용한다. 문서 읽기에 녹음 쓰기용 잠금 검사를 적용하지 않는다.

아래 클래스와 메서드는 새로 작성할 제안이다. 위치는 `backend/src/main/java/com/knot/backend/` 기준이며 이름은 실제 구현에서 역할이 겹치지 않도록 조정한다.

| 위치·클래스 | 책임 | 추가할 메서드 후보 |
| --- | --- | --- |
| document/presentation/DocumentApi | Swagger의 문서 설명·응답·오류·쿠키 인증 계약 | findDetail(workspaceId, documentId, authenticatedMember) |
| document/presentation/DocumentController | 실제 GET 매핑, 인증 memberId 전달, 응답 변환 | findDetail(...) |
| document/application/DocumentDetailService | 활성 권한 검사, 문서 없음 404, 내 상태와 Result 구성 | findDetail(workspaceId, memberId, documentId) |
| document/application/DocumentDetailQuery | Workspace 범위 상세와 집계를 읽는 조회 계약 | findDetail(workspaceId, documentId, memberId) → Optional<DocumentDetailSnapshot> |
| document/infrastructure/DocumentDetailQueryAdapter 및 DocumentJpaRepository | 실제 DB 조회와 application 조회 결과 변환 | findDetail(...) 및 findDetailSnapshot(...) |
| document/domain/MyConfirmationState | 대상 여부·확인 시각으로 내 3상태 판정 | resolve(required, confirmedAt) |
| document/application/dto/result의 DocumentDetailSnapshot/DocumentDetailResult | 조회한 사실과 외부에 반환할 application 결과 | Snapshot은 대상 여부/확인 시각, Result는 판정된 상태를 보유 |
| document/presentation/dto/response/DocumentDetailResponse | API 필드와 Schema, Entity 직접 노출 방지 | from(DocumentDetailResult) |
| 같은 response 패키지의 DocumentConfirmationSummaryResponse | 확인·미확인·제외 집계 record, 별도 파일 | 단순 응답 값 구성 |
| document/domain/DocumentErrorCode·DocumentException | DOCUMENT_NOT_FOUND를 기존 ProjectException에 연결 | 기존 도메인 예외 생성 관례 적용 |

`DocumentApi`는 Swagger 계약을 모으는 인터페이스이고 Controller가 이를 구현한다. Query/Adapter는 실제 다중 테이블 조회를 위해 기존 관례대로 분리한다. Service는 읽기 전용 트랜잭션이며 외부 호출과 저장 변경 없이 Result를 반환한다. GET Request/Command나 별도 UseCase 인터페이스는 필요하지 않다.

## 4. 저장 기반과 주의할 점

| 모델 | 이번에 필요한 저장 내용 |
| --- | --- |
| Transcript | ID, recordingSessionId, 전체 원문 텍스트, 생성 시각. recording 도메인 배치 제안 |
| DocumentGenerationJob | ID, 입력 Transcript, Job 상태·시각. Document의 필수 Job FK를 성립시키는 최소 모델 |
| Document | Workspace·녹음·원문·Job ID, topic/title/summary/content, DRAFT/ARCHIVED, createdAt/archivedAt |
| DocumentConfirmation | documentId/memberId 복합 키와 nullable confirmedAt. 문서 생성 시 대상 스냅샷 |
| DocumentConfirmationId | 복합 키용 별도 JPA class |

Job 모델이 먼저 필요한 이유는 ERD의 Document→Job 필수 연결 때문이다. 이번에 Job 실행·자동 재시도·보존 실행기까지 구현하지는 않는다. 생성 시 Document와 확인 대상의 동시 저장은 #501의 실행 경계에서 보장한다. GET에서 현재 멤버 전원으로 대상 행을 다시 만들지 않는다.

DB에는 `UNIQUE(generation_job_id)`와 `UNIQUE(recording_session_id, topic)`를 둔다. 같은 Job과 같은 녹음·같은 확정 주제의 중복 문서를 차단하고 다른 녹음의 같은 주제는 허용한다. 출처와 Workspace 일치는 FK 설계와 저장 경계에서 검증하며 읽기 JOIN으로 모순을 숨기지 않는다.

필수 컬럼·복합 키·FK·문서 상태/보관 시각의 CHECK를 PostgreSQL에서 검증한다. summary는 nullable, DRAFT의 archivedAt은 null이다. 성공 문서의 연결 원문을 연쇄 삭제하는 cascade를 넣지 않는다.

confirmedAt이 있으면 confirmed, 없고 현재 활성 대상이면 pending, 없고 탈퇴한 대상이면 excluded다. 확인한 뒤 탈퇴한 사람은 confirmed에 남는다. 탈퇴·재가입 이력은 EXISTS 또는 활성 행 조건으로 한 사람당 한 번만 집계한다. 기존 ERD의 현재 멤버십 정책에 따라 DRAFT의 원래 대상이 재가입하면 pending에 포함한다. GET은 ARCHIVED를 되돌리지 않으며 보관 후 상태 전이 정책은 쓰기 작업에서 다룬다.

문서 상태와 집계는 하나의 SQL 결과로 읽는다. readOnly만 선언해서 여러 SELECT의 조회 시점이 같다고 가정하지 않는다.

| 구현 전 확인할 항목 | 현재 근거와 처리 방법 |
| --- | --- |
| Flyway 번호·적용 순서 | 현재 저장소 파일은 V24까지, 열린 #483에 V26 존재. 운영 DB 적용 이력은 미관측. 구현 직전 병합·배포 순서와 번호를 확인하며 outOfOrder로 우회하지 않음 |
| 녹음 길이 출처 | 현재 RecordingSession.accumulatedRecordingMillis/1000 사용 제안. 일시정지 제외·소수 초 버림. 실제 오디오 메타데이터를 정본으로 정한 계약이 있으면 구현 전 맞춤 |
| Persona 작업 카드 | 현재 backlog의 #427 로그인 이력을 보존하며 #496 작업 카드로 연결 |
| #496 ADR | docs/adr/496-document-my-confirmation-state.md가 아직 없음. 원래 결정 snapshot 복구 또는 실제 논의한 대안/선택 근거 재검증 후 materialize_adr.py로 Proposed 생성. 3상태 결정을 재인터뷰하거나 대안을 추측하지 않음 |

Migration은 Transcript→Job→Document→Confirmation 순서로 만든다. 기존 Member/Workspace/Recording 데이터와 JPA validate를 검증한다. 원문의 실제 startMillis·구간 생산은 STT 선행 작업 및 #499에서 연결하며 TEXT에서 시간·화자를 추정하지 않는다.

## 5. TDD와 검증 순서

각 단계는 실패하는 테스트를 만든 뒤 통과시키는 최소 구현으로 진행한다.

1. **시작 준비**: Persona #496 작업 카드, Proposed ADR, migration 적용 순서를 연결한다.
2. **저장 제약**: 필수 필드·FK·복합 키·중복 제한 테스트 → 목적별 migration과 Entity를 만든다.
3. **내 확인 상태**: 미확인·확인·비대상 테스트 → MyConfirmationState 판정을 만든다.
4. **상세 DB 조회**: DRAFT/ARCHIVED·null·확인 집계·Workspace 범위·재가입 이력 테스트 → Query/Adapter를 만든다.
5. **서비스 권한**: 비멤버 403 및 조회 미호출·문서 없음 404·정상 결과 테스트 → DocumentDetailService를 만든다.
6. **HTTP 계약**: ID 전달·응답 JSON·오류 테스트 → DocumentApi/Controller/Response를 만든다.
7. **전체 연결**: 실제 쿠키·Security·PostgreSQL·OpenAPI를 검증하고 구현 기반 API 문서와 Persona 보고서를 정리한다.

| 테스트 위치·방식 | 검증할 동작 | 기대 결과 |
| --- | --- | --- |
| MyConfirmationStateTest, JUnit | 대상 없음·확인 시각 있음·대상 미확인 | NOT_REQUIRED·CONFIRMED·PENDING |
| DocumentDetailServiceTest, Mockito | 정상·권한 없음·문서 없음 | Result·403 및 상세 조회 미호출·404 |
| DocumentMigrationIntegrationTest, PostgreSQL/Flyway | 필수 필드·FK·복합 키·같은 Job/녹음 주제 중복 | 잘못된 저장 거절, 다른 녹음의 같은 주제 허용, 기존 데이터 보존 |
| DocumentDetailQueryIntegrationTest, PostgreSQL | 상태/null·0명 집계·가입/탈퇴/재가입·부분 성공 | 정확한 필드와 중복 없는 집계, 실패 Job과 관계없이 성공 문서 조회 |
| 위 Query 통합 테스트 | 녹음 시간의 1초 미만·초 경계·일시정지 제외 | 정한 밀리초→초 변환 결과 |
| DocumentControllerTest, standalone MockMvc | 경로/principal 전달·JSON·숫자 형식 오류 | 올바른 서비스 인자·응답·400/404 |
| DocumentDetailAcceptanceTest, 실제 Security/PostgreSQL | 쿠키·미인증·비멤버/탈퇴/삭제 Workspace·타 Workspace 문서 | 200/401/403/404, 비참여 현재 멤버도 200 |
| 위 인수 테스트 | GET 전후 저장값·CSRF 없는 GET·시각 표현 | 문서/확인 기록 불변, GET 성공, UTC 표현 |
| 실제 /v3/api-docs | 경로·인증 scheme·응답 필드·enum/null·오류 | 최신 계약과 일치, principal 숨김 |

Mockito는 DB 제약·커밋을, standalone MockMvc는 실제 Security 필터를 증명하지 않는다. 이를 각각 PostgreSQL 통합 테스트와 실제 Security 인수 테스트로 검증한다. 커밋 요청이 있을 때는 스키마·판정·조회 adapter·서비스·HTTP·문서를 작은 단위로 나눈다. 불필요한 refactor 단계는 추가하지 않는다.

현재 build.gradle에서 확인한 검증 명령이다. 아직 실행하지 않았다.

```bash
./gradlew test --tests 'com.knot.backend.document.*'
./gradlew integrationTest --tests 'com.knot.backend.document.*'
./gradlew acceptanceTest --tests 'com.knot.backend.document.*'
./gradlew spotlessCheck
```

공유 schema 변경의 병합 준비에는 전체 test/integrationTest/acceptanceTest와 Persona의 implementation/review 보고서 및 finish implement를 수행한다.

## 6. 완료 기준과 다음 작업

실제 인증된 요청이 저장된 DRAFT/ARCHIVED 상세를 정확히 반환하고, 권한/범위 오류와 내 확인 3상태·집계·DB 제약·조회 불변·Swagger 계약의 검증이 통과하면 #496을 완료한다.

이번 테스트는 저장 fixture를 이용한 조회 검증이다. 실제 업로드→STT→자동 문서 생성의 전체 흐름과 공급자 타임스탬프 지원은 #501 및 STT 작업에서 별도로 검증한다. API 테스트만으로 생성 실행까지 완료했다고 판단하지 않는다.

후속 순서는 **#496 → #495 → #497 → #498 → #499 → #493 → #494 → #501**이다. 기존 docs/harness/notion-alignment.md와 docs/product/current-v2-mvp.md의 사용자 변경을 보존한다.

## 실제 구현 진행 사항

- DocumentApi/DocumentController의 메서드는 findDocument, 상세 Service/Query는 find로 구현했다.
- 다중 테이블 조회는 QueryAdapter에서 JdbcClient로 단일 SQL을 실행한다. 전용 JPA 조회 Repository와 Row는 추가하지 않았다. Entity는 JPA mapping/validate와 실제 저장·재조회로 검증한다.
- V27__create_document_read_models.sql에 최소 4테이블, 출처 복합 FK, Job·녹음 주제 UNIQUE, 확인 복합 키와 보관 상태 CHECK를 구현했다.
- #496 Proposed ADR을 검증된 현재 결정 근거로 materialize했다. 이전/최신 계약 두 대안을 기록했고 팀 승인으로 표현하지 않았다.
- 저장 제약 11개, 상세 Query 8개, HTTP/Security/OpenAPI 13개 및 관련 단위 테스트가 집중 실행에서 통과했다. 신규 테스트는 총 54개다.
- `./gradlew check --console=plain` 통과: 단위 391개, 통합 170개, 인수 262개, 총 823개. 실패·오류·skip은 0이며 spotlessCheck도 통과했다.
- API 구현 계약은 [문서 상세 API](../api/document-detail.md)에 저장했다. V26 선행 합류·배포와 서버 누적 녹음 시간 기준을 명시했다.
- Persona 구현·검토 보고서에 현재 작업을 추가했다. `workflow finish implement`는 기존 작업 상태와 도구의 읽기 근거 경로 문제로 미통과이며 제품 테스트 성공과 구분한다. 과거 #427 pending 카드를 이번 작업 완료로 처리하지 않았다.
- 사용자 후속 요청으로 작업 단위 commit·push와 #496 Draft PR 게시를 진행한다. 배포는 범위에 포함하지 않는다. 다음 구현 대상은 #495이며 이번 작업에서 시작하지 않는다.
