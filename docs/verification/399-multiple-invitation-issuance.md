# #399 I02 복수 초대 발급

## 구현 범위

코드와 링크는 한 초대로 함께 생성하고 기존 expiresAt으로 생성 24시간 뒤 함께 만료한다.
`workspace.invitation.multiple-enabled`의 기본값은 false다. 기본 모드는 기존 단일 발급을
유지하며 true에서는 POST 요청마다 새로운 invitationId·code·linkToken·expiresAt을 반환한다.
신규 코드는 A-Z 6자리다. 기존 숫자 코드와 소문자 정규화도 만료 전까지 서버에서 인정한다.

V18은 Workspace별 단일 미무효화 UNIQUE만 제거한다. hash UNIQUE·24시간 CHECK·FK는
유지하고 Workspace별 생성 시각/ID 정렬 인덱스를 추가한다. 새 발급은 이전 행을 수정하지
않으며 기존 Workspace 잠금과 해시 충돌 시 최대 3회의 새 transaction 재시도를 재사용한다.

발급 응답에는 invitationId가 추가된다. true 모드에서는 구 단일 GET과 reissue를 저장소
조회 전에 404로 차단한다. 공유 응답과 오류에 no-store를 적용하고 OpenAPI도 모드를 따른다.
#400의 OWNER 목록과 #402~#404의 소비자 전환·구 경로 제거는 별도 범위다.

## 공개와 복구 조건

1. 이 PR은 기본값 false로 배포한다. 구 코드와 새 코드가 혼재하는 동안 true로 바꾸지 않는다.
2. #400 및 FE 소비자 전환, 기존 숫자 코드 입력 경로 또는 자연 만료, #404 전환 증거를 확인한다.
3. 모든 writer가 새 코드이며 같은 true 설정을 쓰도록 전환한다. 복수 행이 생긴 뒤에는
   false 설정이나 구 단수 Repository를 사용하는 버전으로 롤백하지 않는다.
4. 문제가 생기면 발급 트래픽을 중단하고 복수 데이터를 읽을 수 있는 수정 버전으로 복구한다.
   기존 초대를 임의 삭제·무효화하거나 Flyway repair로 제약을 복구하지 않는다.

기본값 false의 단일 발급·재발급 회귀와 true의 반복/동시 발급·구 경로 차단을 각각 검증한다.
운영 설정과 DB는 변경하지 않았다. 선행 #443/#411의 develop 통합 migration 번호 문제는
선행 통합 시 실제 적용 이력에 맞춰 해결해야 한다.

## 검증 범위

- OWNER/MEMBER 반복 발급 201, 서로 다른 ID, A-Z 코드, 기존 code/link 미리보기와 행 보존
- 같은 Workspace의 동시 발급 두 건 저장, DB UNIQUE 제거 및 기존 hash 충돌 방어
- 실제 PostgreSQL 충돌 재시도 성공과 3회 소진 rollback, 기존 초대 무효화 없음
- 인증·CSRF·비멤버·탈퇴자·삭제 Workspace 차단, no-store, 구 API 404
- 전체 단위·통합·인수·Spotless·bootJar 및 변경 컨벤션 감사

## 컨벤션 적용

STACK/LIB는 기존 Java25·Spring·JPA·PostgreSQL을 사용하며 의존성을 추가하지 않는다.
LAYER/MODEL은 기존 Service·Repository·DTO 경계를 유지한다. API/DB는 모드별 응답과
전환 제약을 명시한다. JAVA/TEST는 필드·인자 줄바꿈, GWT와 실제 DB 검증을 적용한다.
AI/DELIVERY는 작성자 diff 검토와 실제 실행 결과를 사용하며 #399 I02만 이 PR에 포함한다.
전체 소스 감사의 기존 위반 5건은 범위 밖이며 전체 Gradle 검증은 별도로 실행한다.

최종 로컬 검증: 단위 208·통합 89·인수 139건(총 436건), 실패·오류·건너뜀 0.
Spotless·bootJar·변경 감사·기본 wrapper·Governance 단위 8건 통과. 기존 보안 설정 테스트에
전환 설정 Bean을 포함했고 단일 미무효화 UNIQUE를 요구하던 DB 테스트 3건을 복수 저장
보존 검증으로 변경했다. 기본 모드 Service의 단일 발급·동시 수렴 테스트는 계속 통과한다.
