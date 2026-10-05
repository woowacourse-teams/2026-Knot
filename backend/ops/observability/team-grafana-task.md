# 팀 공용 Grafana 접속 작업 카드

- 승인 근거: 2026-10-01 사용자가 NCP Grafana 유지, 팀별 로그인과 grafana.knoted.kr 주소, Cloudflare 적용을 선택했다.
- 범위: 기존 데이터·6개 대시보드·Discord 알림은 유지하고 Grafana 접속 경계만 변경한다.
- 구현: Cloudflare 프록시 DNS, 전용 원본 인증서, HTTPS Nginx vhost, Grafana 개인 계정 로그인과 Secure cookie.
- 제한: Grafana·Prometheus·Loki는 loopback binding을 유지한다. 공용 vhost는 Cloudflare와 loopback만 허용한다. 자동 Viewer 로그인은 공용 구성에서 비활성화한다.
- 검증: 로그인 화면 200, 미인증 데이터 API 401, 위조 자동 로그인 헤더 401, 직접 원본 접속 403, 기존 수집·알림·대시보드 보존.
- 적용 순서: 인증서 발급 → NCP 로컬 인가 검증 → Cloudflare DNS 프록시와 해당 호스트의 Full (strict) 설정 → 외부 검증.
- 제외: 기존 API/프론트엔드 DNS 변경, 다른 호스트의 SSL 모드 변경, 팀원 계정·비밀번호 임의 생성, commit/push/PR.
- 상태: 2026-10-01 19시대 NCP 적용·Cloudflare DNS·Grafana 전용 TLS strict 규칙 활성화. 외부 HTTP 인가 검증 통과. 브라우저의 새 주소 접근 권한이 거부되어 공용 화면 시각 검증은 미완료.
- 실제 RED: 기존 tunnel용 Grafana에 X-Knot-Viewer: knot-viewer 헤더를 보내면 /api/user가 200이다. 공용 설정 적용 뒤에는 같은 요청이 401이어야 한다.
- 실제 GREEN: NCP loopback·원본 HTTPS·공용 HTTPS에서 login 200, 사용자·검색·데이터 소스·대시보드 API와 위조 Viewer/Admin 헤더 요청 모두 401. Cloudflare를 거치지 않은 원본 HTTPS 접속은 403.
- 보존 확인: 관리자 인증 API에서 대시보드 6개, 데이터 소스 2개와 알림 7개 유지. 모든 알림 health=ok/state=inactive. Dev Spring up=1, 양쪽 API probe_success=1, Loki env=dev/prod 확인.
- TLS 조치: Nginx reload 직후 이전 인증서가 응답하는 현상을 재현했다. 2초 뒤 정상 인증서와 login 200을 확인했고, 배포 스크립트에 검증된 TLS의 제한된 준비 대기를 추가했다. 인증서 검증을 끄지 않았다.
- 복구 백업: /opt/knot-observability/team-backup.Xazynt. 팀 계정 임의 생성·관리자 비밀번호 변경·commit/push는 하지 않았다.
- 팀원 개인 계정 초대와 로그인 후 공용 대시보드의 실제 브라우저 확인은 별도 단계다. 현재 관리 계정의 로컬 API 인증만 검증했다.
