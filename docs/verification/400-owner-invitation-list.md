# #400 OWNER의 유효 초대 목록

GET /api/v1/workspaces/{workspaceId}/invitations는 현재 활성 OWNER만 사용할 수 있다.
Workspace 미존재·삭제는 404, MEMBER·비멤버·탈퇴자는 403이며 미인증은 401이다.

생성 시각 이하·공통 expiresAt 이전이며 invalidatedAt이 없는 행만 Workspace별로 조회한다.
정렬은 createdAt DESC, invitationId DESC다. 한 번 읽은 서버 시각으로 필터링하고
invitationId·createdAt·expiresAt만 배열로 반환한다. 없으면 빈 배열이다.
원문·해시·암호문은 DTO와 OpenAPI에 없고 응답과 오류는 no-store다.

읽기 전용 Service에서 존재·OWNER 권한을 확인한 뒤 JPA 쿼리로 필터·정렬한다. 조회는
발급·무효화·연장·멤버십 보정 등을 하지 않는다. 기존 단일 GET/reissue 종료 정책은
선행 I02의 설정을 따르며 이 PR에서 추가 제거하지 않는다. 신규 migration은 없다.

## 검증

실제 PostgreSQL과 MockMvc로 다음을 검증한다.

- 다수 유효 초대와 생성 시각 동률 ID 역순 정렬, 다른 Workspace 격리
- 만료 정각·이후·미래 생성·과거 무효화 제외 및 빈 배열
- ID·생성·만료 시각 외 응답 필드 부재와 공통 24시간 관계
- OWNER 승계 후 새 OWNER 허용, 이전 OWNER·MEMBER·탈퇴자·비멤버 거절
- 삭제·미존재 404, ID 오류 400, 인증 401 및 no-store
- 초대·참여 이력 전체 JSON snapshot 무변경, OpenAPI 스키마·응답·인증 계약

최초 OpenAPI 검증은 테스트의 문서 공개 설정이 false여서 401이었다. 테스트 context에
knot.api-docs.enabled=true를 명시한 뒤 11건이 통과했다. 운영 설정은 바꾸지 않았다.

## 컨벤션과 경계

STACK/LIB는 기존 스택·의존성, LAYER/MODEL은 Repository·Service·결과/응답 DTO 분리를
사용한다. API는 기존 오류·배열·시간 계약, DB는 조회만, JAVA/TEST는 GWT·필드/인자 규칙과
실제 PostgreSQL 검증을 적용한다. AI/DELIVERY는 실제 diff 검토와 실행 결과를 근거로 한다.
FE 화면 전환과 운영 배포는 범위 밖이며 기존 전체 컨벤션 위반은 별도로 보고한다.
