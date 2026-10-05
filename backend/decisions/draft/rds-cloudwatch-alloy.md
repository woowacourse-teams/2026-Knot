# 기존 EC2 역할로 RDS CloudWatch 지표 수집

## 상태

draft

- 작성 계기: 코드·호스트 관찰과 사용자의 관측 작업 지속 지시.
- 관찰 근거: `ops/observability/agent/config.alloy`, Dev·Prod EC2의 실제 CloudWatch 조회 성공, Dev DB_URL의 `knot-dev-database` 호스트.
- 운영 작업 범위 승인과 팀의 ADR Accepted 상태는 별개다.

## 문제 상황

RDS 화면이 비어 있어 Hikari 풀 문제와 RDS 자체 지연을 구분할 수 없다.
첨부안의 Grafana CloudWatch 직접 연결과 기존 EC2 Alloy를 통한 수집을 비교했다.
NCP에는 AWS 역할이 없지만 두 EC2는 기존 역할로 해당 DB의 CloudWatch 값을 조회할 수 있다.

## 선택한 방향

각 EC2의 Alloy가 자신의 환경에 매핑된 RDS 인스턴스의 핵심 9개 지표만 수집한다.
기존 mTLS remote-write로 NCP Prometheus에 전달하고 기존 Grafana 데이터 소스를 재사용한다.
AWS 키·IAM 역할·새 관측 서버는 만들지 않는다. 5분마다 CloudWatch를 조회하고
데이터 부재를 0으로 바꾸지 않는다.

## 선택 이유

### 기존 인증과 수집 경로 재사용

EC2 역할의 임시 인증을 사용하므로 NCP나 저장소에 AWS 키가 필요 없다.
DBInstanceIdentifier를 정적으로 지정해 다른 팀 DB는 조회하지 않는다.

### 환경 라벨 유지

Dev는 `knot-dev-database`, Prod는 `knot-database`로 명시한다.
호스트의 `env` 라벨을 그대로 사용하고 Prod RDS를 Dev 지표로 표시하지 않는다.

## 트레이드오프

Grafana에서 CloudWatch 임의 지표를 직접 탐색하는 기능은 없다.
CloudWatch 조회 API 사용량이 늘고 5분 집계와 수집 지연이 생긴다.
DB 이름·리전 변경 시 설정을 갱신해야 하며, 수집기 상태가 정상이어도 AWS 조회 실패는 별도로 확인해야 한다.

## 현재 판단

승인된 관측 작업의 연결 방법을 기록하는 draft다. 실제 서버 수집·NCP 도착 검증은
운영 기록에 별도로 남긴다. 팀 리뷰 전에 Accepted로 이동하지 않는다.

## 재검토 신호

- RDS 인스턴스·리전 또는 EC2 역할이 변경된다.
- 더 짧은 집계 간격이나 CloudWatch 직접 탐색이 필요하다.
- 조회 비용·Alloy 자원 사용량이 운영 기준을 넘는다.
