# NCP Grafana의 팀 공용 접속

## 상태

draft

- 작성 계기: 반복 질문 / 기본 입장 충돌 / 사용자 답변.
- 사용자 선택: 2026-10-01 NCP Grafana 유지, grafana.knoted.kr, Cloudflare 적용. 팀 리뷰 승인은 별도다.
- 관찰 근거: ops/observability/compose.yml의 loopback binding과 auth proxy, nginx/grafana-local.conf의 고정 Viewer 헤더. NCP 기존 인증서의 SAN은 dev-api.knoted.kr뿐이다.

## 문제 상황

localhost SSH 터널은 각자 별도 연결이 필요해 팀 공용 링크로 사용할 수 없다.
기존 팀 Grafana에 데이터 소스를 연결하는 대안과 NCP Grafana를 계속 사용하는 대안을
설명했으며, 사용자가 NCP Grafana 유지와 팀 공용 주소를 선택했다.

## 선택한 방향

NCP 수집·저장·대시보드·알림은 유지하고, Cloudflare 프록시와 전용 HTTPS vhost를 추가한다.
공용 구성에서는 고정 Viewer 자동 로그인을 끄고 Grafana 개인 계정 로그인을 사용한다.
개인키는 NCP에서 생성하고 저장하며 Cloudflare에는 공개 CSR만 제출한다.
Grafana·Prometheus·Loki의 loopback binding을 유지하고 공용 vhost의 원본 연결은
Cloudflare IP와 loopback으로 제한한다. 다른 호스트의 TLS 정책은 변경하지 않는다.

## 선택 이유

### 기존 운영 자산 유지

이미 생성한 6개 대시보드와 Discord 알림을 재구성하지 않고 팀 공용 화면으로 사용한다.

### 사용자별 권한

조회는 Viewer, 필요한 수정·관리는 Editor/Admin으로 분리한다. 팀원 명단과 로그인
자격 증명이 제공되지 않은 상태에서 계정을 추측해 생성하거나 공유 비밀번호를 배포하지 않는다.

## 트레이드오프

NCP Grafana의 백업·패치·계정 관리와 원본 인증서 갱신을 팀이 담당한다.
Cloudflare 프록시를 해제하면 Origin CA 인증서는 일반 브라우저에서 신뢰되지 않는다.
고정 Viewer SSH 자동 로그인은 공용 구성 적용 후 더 이상 사용하지 않는다.

## 현재 판단

이번 구현의 권한 근거는 사용자의 명시적 주소·구성 선택이다. 이 draft 자체를
승인된 팀 표준이나 구현 완료 증거로 사용하지 않는다. DNS 게시·서버 적용·실제 조회는
작업 카드와 운영 문서에 각각 별도로 기록한다.

## 재검토 신호

- 팀 계정·SSO가 기존 중앙 Grafana로 통합된다.
- NCP 운영·백업 담당자를 유지할 수 없다.
- 로그에 개인정보가 들어가 데이터 소스 단위 권한 통제가 필요해진다.
- Cloudflare 프록시나 Origin CA 인증서를 교체한다.
