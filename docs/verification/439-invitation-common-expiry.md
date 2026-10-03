# #439 초대 공통 만료와 데이터 보존 검증

## 범위

#399의 I01이며 선행 #411의 `93d02290` 위에 구현한다. 사용자는 #443 검토 중
코드·링크 동시 생성과 공통 24시간 만료를 채택했다. 앞선 독립 만료 확장을 제거하고
기존 `WorkspaceInvitation.create`, `expiresAt`, `isValidAt`을 그대로 사용한다.

- 상위 #399, 현재 #439, 하위 이슈 없음. Proposed ADR399를 최신 결정에 맞췄다.
- 두 만료 필드·별도 생성/판정·fallback·전용 오류/테스트를 제거했다.
- 기존 Domain·발급·preview·accept의 공통 만료 검증은 유지한다.
- V17은 Workspace FK를 RESTRICT로 바꾸고 초대 이력을 보존한다.
- 공통 expires_at·고정24시간 CHECK·hash UNIQUE·단일 미무효화 UNIQUE를 유지한다.
- 복수 발급·A-Z 코드 전환·구 원문 조회/재발급 종료는 후속 이슈다.

## 요구사항과 근거

| 조건 | 근거 |
| --- | --- |
| 동시 생성·공통 24시간 만료 | 기존 Domain create와 isValidAt, 기존 발급·preview·accept 테스트 |
| 정확한 만료부터 거절·무효 이력 보존 | 기존 WorkspaceInvitationTest의 경계·invalidated 검사 |
| 기존 행·효력 보존 | V15→V17 upgrade 전후 전체 기존 컬럼과 유효/만료/무효 행 비교 |
| 기존 쓰기·CHECK 보존 | 기존 INSERT 성공, 0·음수·23시간 만료 거부 |
| 물리 삭제 차단 | PostgreSQL18 SQLSTATE 23001과 부모·초대 행 보존 |
| 기존 API·동시성 | 전체 인수 및 실제 PostgreSQL 테스트 유지 |

## Migration 경계

V17은 이 Draft에서만 추가된 변경을 FK 전용 파일로 정리했다. 기존 V1~V15는 수정하지
않았다. 독립 만료 컬럼·backfill은 필요 없다. 로컬 검증은 폐기 가능한 Testcontainers
DB에서 수행했다. 이전 Draft V17을 별도 공유 DB에 적용했다면 동일 버전 파일로 교체하거나
Flyway repair를 실행하지 말고 적용 이력에 맞는 후속 migration을 별도로 준비해야 한다.
운영 DB 이력은 확인하지 않았고 운영 쓰기·repair는 수행하지 않았다.

최신 develop의 V15 인증 세션·V16 회원 삭제와 선행 #411 V15 번호 충돌은 develop 통합
전에 적용 이력을 확인해 해결해야 한다. 이 스택의 CI는 develop 통합 배포 증거가 아니다.

## 적용 장부

| 규칙군 | 적용 |
| --- | --- |
| STACK/LIB | Java25·기존 Spring/JPA/PostgreSQL18, 새 의존성 없음 |
| LAYER/MODEL | 기존 Domain·Repository·HTTP 유지, 독립 만료 확장 제거 |
| API | 기존 공개 응답·오류 계약 유지 |
| DB | FK RESTRICT와 데이터 보존, 기존 CHECK·UNIQUE 유지 |
| JAVA | 기존 코드 재사용, 테스트 필드·인자 줄바꿈·Spotless |
| TEST | 기존 Domain 경계·전체 인수와 PostgreSQL 회귀, GWT 단일 행위 |
| AI | 독립 읽기 전용 검토와 실제 실행 결과로 판단 |
| DELIVERY | 동일 #439/#443 갱신, 문서·삭제 포함 PR 1000줄 이내 |

## 검증 결과

- `./gradlew test integrationTest acceptanceTest bootJar --rerun-tasks --no-parallel`:
  단위 208·통합 89·인수 128건, 총 425건 성공. 실패·오류·건너뜀 0.
- 먼저 Domain과 관련 migration 테스트를 좁혀 실행했고 통과했다.
- Spotless, 원문 hash, 변경 파일 컨벤션 감사, Governance 단위 8건이 통과했다.
- 기존 전체 컨벤션 위반 5건은 범위 밖이다. 전체 wrapper는 이 지점에서 중단하므로
  전체 Gradle task를 별도로 실행했다.
- 이슈 계약 6건은 사용자 결정에 따라 재검증했다. #439 계약 `953000337ca38d8f` Pass,
  자료 충분으로 인터뷰 생략. dry-run `render_draft`, `remote_write_authorized=false`이며
  기존 이슈 수정은 사용자의 수정·push 지시 및 이전 이슈 정합성 수정 권한으로 별도 수행한다.
