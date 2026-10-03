# 환경별 대시보드와 알림

관련 Issue: [#433](https://github.com/woowacourse-teams/2026-Knot/issues/433).

`Knot-dev`는 전체 상태, API 요청·오류, 로그, 서버 자원, Spring 앱,
Tomcat·DB 풀, RDS의 기본 7개 화면을 제공한다. `Knot-prod`는 전체 상태,
API 요청·오류, 로그, 서버 자원, RDS·별도 AWS DB의 5개 화면을 제공한다.
기존 `Knot` 공통 6개 화면은 알림의 UID·패널 링크 때문에 유지한다.

모든 새 쿼리는 `env`를 고정한다. Dev는 Spring이며 Prod는 프로토타입이다.
Prod의 실제 앱 DB는 Docker PostgreSQL이다. `knot-database` RDS는 별도 AWS
DB이며 현재 Prod 앱 DB 상태를 뜻하지 않는다. Prod에는 Spring/JVM/Tomcat/Hikari
패널을 두지 않는다. 서버 자원은 AWS 앱 서버 지표이고 NCP 관측 서버 지표와 구분한다.
RDS 식별자는 메트릭 라벨이며 DB 접속 주소나 인증정보가 아니다.

Stat은 현재 상태, Gauge는 현재 사용률, Time series는 변화, State timeline은
health 이력, Table은 기간 내 URI·상태별 요청, Logs는 원문, Dev Heatmap은 실제
histogram 분포를 보여준다. 데이터 없음은 요청 부족이나 수집 장애일 수 있으므로
0이나 정상으로 대체하지 않는다. 그래프 사이의 빈 구간도 연결하지 않는다.

## 생성과 검증

백엔드 디렉터리에서 실행한다.

```bash
node ops/observability/build-environment-dashboards.mjs --write
node --test ops/observability/environment-dashboards.test.mjs ops/observability/discord-notifications.test.mjs
python3 ops/observability/deployment.test.py
./gradlew spotlessCheck test integrationTest acceptanceTest bootJar
```

17175 revision 2는 선택적 Dev 8번째 화면이다. 공개 저장소에는 원본 JSON과
생성 결과를 포함하지 않는다. 원본의 재배포 라이선스가 확인되지 않았기 때문이다.
운영자가 [공식 카탈로그](https://grafana.com/grafana/dashboards/17175-spring-boot-observability/)의
revision 2를 저장소 밖에 준비하고 `KNOT_CATALOG_SOURCE`에 그 경로를 지정한다.
생성기는 원본 SHA-256을 검증한 후 기존 Prometheus·Loki와 Dev Spring 라벨에 맞춘다.

```bash
node ops/observability/build-catalog-dashboard.mjs
node --test ops/observability/catalog-dashboard.test.mjs
```

17175는 앱 시작 이후 누적 요청과 최근 2분 지연을 구분하며 health 요청을 포함한다.
기존 API 화면은 Nginx 기준이다. Tempo·OpenTelemetry·exemplar와 trace ID 이동은
미구축이며 연결했다고 표시하지 않는다. 원본 패널 배치와 KST·30초 갱신을 유지한다.

## 지표와 로그 수집

`application-observability.properties`는 Prometheus 노출, HTTP histogram,
Tomcat MBean을 활성화한다. Micrometer Prometheus registry는 Spring Boot의
dependency management를 따르는 런타임 의존성이다. Actuator만으로는 Prometheus
registry가 없어 필요한 내부 지표가 수집되지 않는다. 이미 승인된 Prometheus 수집
경로에 연결하는 범위이며, 관측 수집을 제거할 때 이 의존성과 profile을 함께 제거할 수 있다.

`agent/systemd-observability.conf`는 기존 Dev 서비스에 관측 profile을 포함하는
drop-in 예시다. `agent/config.alloy`는 Dev에서만 Spring을 scrape하며 양쪽 환경의
호스트·로그·별도 RDS 지표를 기존 파이프라인으로 전달한다. `KNOT_ENV`와
`KNOT_INGEST_BASE_URL`은 운영자가 기존 승인된 값을 주입한다. 수집 URL은 끝에
슬래시가 없어야 한다. TLS 인증서는 기존 배포 자산을 사용하고 저장소에 넣지 않는다.
Prod 실행 서비스와 자동배포의 일치 작업은 별도 범위다.

SecurityConfig는 실제 remote address가 loopback인 GET metrics만 허용한다.
다른 methods·하위 paths와 인증된 외부 사용자의 접근도 차단한다. Forwarded 헤더로
loopback 접근을 얻을 수 없다. 공개 Nginx에는 `nginx/private-metrics.conf`의 차단
규칙이 필요하다. 공개 프록시가 loopback으로 요청을 전달하기 때문이다.
`access-log.conf`는 토큰·Authorization·cookie·쿼리 문자열을 로깅하지 않는다.
URI 경로 자체의 개인정보와 앱 로그는 별도의 운영 보존·접근 정책을 따라야 한다.

## Discord

기존 Webhook은 `$DISCORD_WEBHOOK_URL`로 주입한다. 발생·해제·테스트를 구분하고
환경, 문제, 확인 순서, KST 시각, 전체 상태·로그·알림 상세를 표시한다. 링크의
호스트는 Grafana `ExternalURL`을 사용하므로 기존 Grafana 외부 URL 설정이 필요하다.
환경이 빠지면 환경 확인 필요로 표시한다. `Updated`에 따른 규칙 해제나 일반 해제를
서비스 복구로 단정하지 않는다. 묶음은 두 건까지만 표시하고 추가 건은 알림 목록으로 안내한다.

표시 문구와 템플릿 외에 기존 15개 규칙의 조건·쿼리·대기 시간·라우팅·수신처와
공통 대시보드 링크를 보존한다. 기존 운영 설정과 달라지면 적용 도구가 차단한다.

## 기존 서버에 적용하는 도구

이 브랜치의 소스 공개 작업은 서버에 추가 적용하거나 테스트 메시지를 보내지 않는다.
다음 절차는 별도 적용 승인이 있을 때 기존 서버에서 수행한다.

stage에는 `provisioning/dashboards/knot.yml`, `provisioning/alerting/`의 두 JSON,
`provisioning/environment-dashboards/dev/`와 `prod/`를 함께 준비한다. stage 루트의
`manifest.json`은 이 파일들의 상대 경로를 키, 파일 SHA-256을 값으로 갖는 객체다.
운영 상태에 17175가 있으면 같은 화면을 생성해 stage에 넣고 `--dev-count 8`을 사용한다.
기존 화면을 빠뜨린 stage는 거부된다.

```bash
python3 deploy-environment-dashboards.py --stage /path/to/approved-stage --dev-count 8
python3 deploy-environment-dashboards.py --stage /path/to/approved-stage --dev-count 8 --apply
```

도구는 기존 read-only 마운트·공급자·알림 계약을 확인하고 운영 파일과 API 설정을
서버 내 0700 백업에 보관한다. 서버의 기존 admin 사용자와 비밀번호 파일을 내부에서
읽으며 출력하지 않는다. 변경된 자산만 provisioning reload하고 기존 공통 파일,
컨테이너 환경, 알림 계약과 템플릿을 다시 확인한다. 새 credentials나 네트워크는 만들지 않는다.

실패하면 원래 파일을 복원하고 reload를 시도한다. `disableDeletion: true` 때문에
새로 등록된 대시보드나 템플릿 API 자산까지 자동 삭제되는 완전한 rollback은 아니다.
실패 시 비공개 백업과 실제 Grafana 상태를 확인해야 한다. 백업·API 응답·실제 접속
정보와 운영 로그는 공개 저장소에 넣지 않는다. SSH 기반 `check-dashboard-data.mjs`는
기존 승인된 접속을 통해 쿼리·링크·배치를 확인하는 도구이며 출력도 비공개로 취급한다.

## 검증 범위

2026-10-03 소스 검증에서 대시보드·Discord·17175 Node 테스트 20개, Python 배포
테스트 4개, Gradle 단위 192개·통합 56개·인수 109개와 포맷·JAR 빌드가 통과했다.
인수 테스트에는 IPv4·IPv6 loopback 지표 성공, Forwarded 위조 차단, 공개 health 유지,
인증된 외부 metrics 차단 5개가 포함된다.

선행 운영 작업에서는 Dev 8개·Prod 5개·공통 6개와 Preview 8개를 확인했다.
기존 Discord 수신처 검증 후 Prod 테스트 API 성공을 확인했으나, Dev 테스트는
HTTP 200 이후 응답 본문 검증이 완료되지 않았다. 사람의 새 메시지 수신은 확인하지 않았다.
실제 AWS 자동배포 대상과 작은 화면·키보드 QA는 별도 확인이 남아 있다.
이번 공개본의 `ExternalURL` 템플릿과 수집 URL 매개변수화는 운영에 추가 배포하지 않았다.
