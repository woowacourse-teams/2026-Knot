# Knot Workspace별 단일 Notion Public OAuth Connection을 사용하고 현재 OWNER가 연결을 생성·교체한다.

## 상태

Proposed

## 관련 Issue

- #254 [BE] 워크스페이스 Notion OAuth 연결 및 상태 조회 구현

## 한 줄 요약

Knot Workspace별 단일 Notion Public OAuth Connection을 사용하고 현재 OWNER가 연결을 생성·교체한다.

## 왜 이 결정이 필요했나

Knot은 여러 팀의 Notion 데이터를 가져오지만 사용자가 installation token을 직접 입력하게 하지 않는다.

현재 OAuth Connection이 없어 연결 화면이 OAuth를 시작하거나 상태를 판단할 수 없다.

결정 동인:

- token 직접 입력 제거
- workspace별 Notion 연결
- OWNER 중심 권한
- page picker 승인
- 단일 Connection 경계

## 트레이드 오프

- Internal Connection과 수동 installation token
- Public OAuth와 page picker 승인

## 무엇을 결정했나

Knot Workspace별 단일 Notion Public OAuth Connection을 사용하고 현재 OWNER가 연결을 생성·교체한다.

Public OAuth는 수동 secret 전달을 없애고 각 Knot Workspace가 자신의 Notion workspace를 연결하게 한다. OWNER 변경 비용은 REAUTH_REQUIRED로 관리한다.

## 결과

- OAuth state는 256-bit 난수로 발급하고 10분 뒤 만료한다. 원문은 authorization URL과 callback에서만 이동하며, DB에는 HMAC만 저장한다.
- state는 한 번만 소비한다. 같은 Knot Workspace에서 새 연결을 시작하면 이전 미완료 state를 무효화하고, callback 완료 전 더 최신 흐름이 생기면 이전 결과로 Connection을 교체하지 않는다.
- access token과 refresh token은 key version을 포함한 AES-GCM envelope로 저장한다. 원문 token은 로그·API 응답·OpenAPI 예시에 노출하지 않는다.
- authorization, token, callback URI는 HTTPS만 허용하고 로컬 개발 주소에만 HTTP를 허용한다.
- token 응답의 Notion workspace, bot, owner type·user ID, template, request 식별정보를 Connection에 보존한다. owner의 이름·이메일·프로필 이미지는 저장하지 않는다.
- OAuth 취소, 만료, 재사용, OWNER 변경, Notion 오류가 발생하면 기존 Connection과 Import 데이터를 변경하지 않고 고정된 실패 화면으로 보낸다.
- OWNER 변경 시 새 승인이 필요하다.
- 승인된 페이지 범위만 접근한다.
- 후속 API가 단일 Connection과 tenant 격리를 따른다.
- Import 실행, token refresh·revoke, 연결 해제와 가져온 데이터의 soft delete는 후속 Issue에서 다룬다.

## 다시 논의해야 할 조건

- 단일 관리형 Notion workspace 전용이 될 때
- Notion OAuth 정책이 바뀔 때
- OWNER 재승인이 반복 장애가 될 때
- team-owned connection이 필요할 때

## 확인

- 예정 경로: `docs/adr/254-notion-public-oauth-connection-policy.md`
- 결정 주체: 임현성
- AI 하네스가 Proposed ADR 파일을 생성했다.
- 팀이 PR에서 승인한 뒤 Accepted로 바꾼다.
