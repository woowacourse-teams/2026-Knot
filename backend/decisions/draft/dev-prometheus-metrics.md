# Dev Spring Prometheus 내부 지표 수집

## 상태

draft

- 작성 계기: 사용자 재배포 승인과 설치 패키지 관찰.
- 관찰 근거: Dev /opt/knot-backend/app.jar에는 Actuator·Micrometer core만 있고 Prometheus registry가 없다. 기본 application.properties는 health만 노출한다.
- 사용자 승인: 2026-10-01 관측성 변경만 추가한 Dev 재배포. 팀 Accepted 결정이나 Prod 교체 승인이 아니다.

## 문제 상황

서버 CPU·메모리·요청 로그만으로는 JVM GC, HTTP 처리, Tomcat 및 Hikari 포화를 구분할 수 없다.
검토한 선택지는 기존 호스트·로그 관측만 유지하거나, Spring 공식 Micrometer Prometheus registry를 연결하는 것이다.

## 선택한 방향

Boot 의존성 관리에 맞는 runtimeOnly registry를 추가하고 observability profile에서만 지표 endpoint와 HTTP histogram을 노출한다.
GET 지표 접근은 앱에서 loopback 주소만 허용하고 Nginx는 해당 경로를 외부에 403으로 차단한다.
Dev Alloy가 localhost에서 scrape한 뒤 기존 mTLS remote-write 경로로 NCP에 전달한다.

## 선택 이유

### 기존 구현과 배포 범위 보존

배포된 develop 버전의 전체 애플리케이션 리소스·클래스 133개가 기준 빌드와 동일함을 확인한 뒤 관측성 변경만 넣는다.
현재 인증 작업 브랜치의 제품 변경과 DB migration은 배포하지 않는다.

### 프레임워크 기본 계측 재사용

기존 Actuator의 JVM·Tomcat·Hikari와 요청 계측을 재사용하며 별도 agent와 비즈니스 계측 코드를 추가하지 않는다.
[Spring Boot metrics 문서](https://docs.spring.io/spring-boot/reference/actuator/metrics.html)의 Prometheus registry 방식을 사용한다.

## 트레이드오프

Dev 재시작이 한 번 필요하고 histogram 시계열·메모리 비용이 추가된다.
loopback 제한은 Nginx 차단과 함께 유지해야 한다. 미래 forwarded-header 설정 변경 시 접근 테스트를 다시 수행한다.
다음 CodePipeline 배포가 원래 develop을 빌드하면 이 임시 배포는 사라진다. 영속 반영에는 별도 commit/push 승인이 필요하다.

## 현재 판단

사용자 승인된 Dev 관측 검증에만 적용한다. Prod 프로토타입에는 Spring 전용 지표를 임의로 붙이지 않는다.
이 문서는 실행 근거와 검토 대안을 보존하는 draft이며 팀 ADR 승인을 대신하지 않는다.

## 재검토 신호

- Prod가 Spring으로 전환되거나 배포 pipeline의 소스와 실제 서비스가 일치한다.
- 수집기의 메모리·시계열 수가 증가하거나 URI 라벨 cardinality가 커진다.
- trusted proxy와 forwarded-header 처리 방식이 바뀐다.
- 다음 develop 자동 배포 전에 관측성 변경을 저장소에 영속 반영한다.
