# #448 멤버십의 가입 초대 출처 저장 기반

## 범위

상위 #403의 I05이며 #447의 `be/chore/#402` (`3e369a48`)를 base로 한다.
WorkspaceMember에 nullable·생성 후 변경 불가인 sourceInvitationId를 추가한다.
기존 create는 출처를 NULL로 두고, createFromInvitation은 양수 초대 ID를 가진
MEMBER·lastViewed=false 참여를 만든다. 기존 생성자 자동 참여를 추측해 연결하지 않는다.

수락 Service는 이번 PR에서 바꾸지 않는다. 실제 초대 수락 시 출처를 전달하는 작업은
I06이며, 이 PR만 배포하면 기존 수락 경로는 계속 NULL을 저장한다. 공개 API·DTO·
인증·CSRF·코드/링크 공통 24시간 만료와 재가입 정책은 그대로다.

## 저장 불변식

- V20은 source_invitation_id만 nullable로 확장하며 기존 행을 backfill하지 않는다.
- source_invitation_id와 workspace_id의 복합 FK로 다른 Workspace 초대 연결을 거부한다.
- 참조 대상 (id, workspace_id)의 UNIQUE는 PostgreSQL 복합 FK에 필요하다.
  멤버십의 source_invitation_id에는 UNIQUE가 없어 여러 멤버가 같은 초대를 참조한다.
- FK RESTRICT는 활성·탈퇴 이력 모두의 출처 초대 물리 삭제를 막는다. 참조 검사에 쓰는
  (source_invitation_id, workspace_id) 부분 index는 NULL 이력을 제외한다.
- Domain은 null·0·음수 초대 ID를 거부하고, DB도 비양수 값과 잘못된 참조를 거부한다.
- sourceInvitationId는 JPA updatable=false와 공개 변경 메서드 부재로 기존 출처를 유지한다.
  임의 운영 SQL 변경을 금지하는 trigger를 추가한 것은 아니다.

## 완료 조건과 증거

| 조건 | 검증 |
| --- | --- |
| 기존/생성자 출처 NULL | 기존 create 단위·OWNER JPA 저장 왕복·V19→V20 기존 행·구 INSERT |
| 실제 출처와 가입 시각 | 실제 초대 저장 뒤 멤버십 save·flush·clear·재조회 |
| 같은 초대의 여러 멤버 | 같은 source ID를 두 멤버가 저장·재조회 |
| 재가입의 새 출처 | 저장한 과거 행을 승계·탈퇴 후 다른 초대로 새 MEMBER 저장, 양쪽 출처/역할/시각 보존 |
| 잘못된 출처 거부 | Domain null/0/음수, PostgreSQL 미존재/다른 Workspace FK와 양수 CHECK |
| 참조 이력 보존 | 탈퇴 멤버십이 참조하는 초대 삭제 실패, 초대·멤버십 행 보존 |
| 기존 동작 | 기존 Domain·Repository·전체 인수 suite |

## 검증과 적용 장부

| 규칙군 | 적용 |
| --- | --- |
| STACK/LIB | Java25·기존 Spring/JPA/PostgreSQL18, 새 의존성 없음 |
| LAYER/MODEL | scalar FK·Entity 생성 책임, 기존 Repository 자동 매핑 재사용 |
| API | 공개 HTTP 변경 없음; 내부 오류는 기존 ErrorCategory·한글 메시지 |
| DB | 새 V20·명시적 RESTRICT·복합 FK·기존 행 보존, Hibernate validate와 실제 PostgreSQL |
| JAVA | 기존 Getter·필드 블록·인자 줄바꿈·Spotless |
| TEST | GWT 단일 행위, 단위/DB/기존 인수 검증 |
| AI | 작성자가 Domain·DDL·테스트·base-to-head diff를 직접 검토 |
| DELIVERY | #403 하위 #448, #447 스택 base, 테스트·문서 포함 1000줄 이내 |

Issue 계약 `843625b45f51bbc6`은 Pass, 자료 충분으로 인터뷰 생략이다.
게시 하네스의 action=publish_issue, remote_write_authorized=true를 확인했다.
기존 Proposed ADR399를 참조하며 새로운 제품 결정을 추가하지 않았다.

- 첫 Gradle 실행은 실제 존재하는 settings.gradle을 daemon이 읽지 못해 실패했다.
  --no-daemon으로 다시 실행했다. 파일 권한·제품 설정은 바꾸지 않았고 근본 원인은 미확정이다.
- migration fixture의 GENERATED ALWAYS 회원 ID 직접 INSERT를 OVERRIDING SYSTEM VALUE로
  교정했다. 제품 DDL은 유지했고 관련 DB 테스트를 다시 통과시켰다.
- 기존 Workspace 탈퇴 migration 테스트의 최신 적용 목록에 V20을 추가했다.
- `./gradlew --no-daemon spotlessApply test integrationTest acceptanceTest bootJar --no-parallel` 성공.
  XML 기준 단위 286·통합 126·인수 214건, 총 626건이며 실패·오류·건너뜀은 0이다.
- 변경 범위 컨벤션 감사·원문 hash 검사·Governance 단위 8건이 통과했다.
  전체 wrapper는 기존 컨벤션 위반 43건에서 중단했다. 해당 15파일은 모두
  PR base `3e369a48`과 바이트 단위로 같으며 이번 변경 밖이다.
- 운영 DB·FE 배포는 검증하지 않았다. 출처를 기록하기 시작한 뒤 구 수락 writer로 되돌리면
  새 가입의 출처가 NULL이 될 수 있으므로 I06 공개 시 writer 전환을 함께 관리해야 한다.
