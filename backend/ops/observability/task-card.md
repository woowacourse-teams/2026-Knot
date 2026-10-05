# 관측성 내부 지표 연결 작업 카드

- 승인: 2026-10-01 사용자가 관측성 변경만 추가해 AWS Dev 재배포를 승인했다.
- 기준: 현재 설치 JAR과 develop 기준 빌드의 애플리케이션 클래스 비교. 인증 작업 브랜치를 배포하지 않는다.
- 구현: Prometheus registry, opt-in observability profile, loopback 전용 GET 인가, Nginx 외부 차단, Alloy Spring scrape.
- 검증: 기존 health 유지, 로컬 지표 200, 원격 지표 차단, Spring/JVM/Hikari 실데이터의 NCP 도착, 기존 로그인 경계 회귀 테스트.
- 화면: 6개 대시보드 역할 분리. Prod 프로토타입과 RDS 미연결을 정상 데이터로 대체하지 않는다.
- 제외: Prod 앱 교체, DB schema 변경, Git commit/push, 원격 저장소 게시.
