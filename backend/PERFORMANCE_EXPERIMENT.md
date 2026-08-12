# Notion 저장 모델 성능 실험

## 목표

Notion API 구조를 관계형 A안 DDL에 저장했을 때 발생하는 시드·조회·메모리 비용을 재현한다. 같은 논리 문서를 Batch 조회, Render Snapshot, Compact JSONB 모델로 읽어 개선 정도와 새 비용을 함께 비교한다.

현재 백엔드에는 Notion Importer와 운영 Entity/Repository/API가 구현되어 있지 않다. 따라서 이 단계는 전체 A안 DDL을 생성하되 Page/Block/Rich Text/Mention/Snapshot 수직 슬라이스를 합성 SQL로 채우는 **DB 저장 모델 실험**이다. `SEED_LOAD`는 실제 Notion Import 처리량이나 앱 HTTP 응답 시간이 아니다. Importer와 조회 API가 생기면 같은 fixture를 서비스/API 경로로 통과시키는 2단계 측정을 추가한다.

`baseline-a.sql`은 현재 v2.1 DDL을 복제한다. 원본에서 삭제 정책을 생략한 두 FK(`workspaces.created_by_user_id`, `notion_connections.connected_by_user_id`)만 프로젝트 DB 규칙에 따라 기본 `NO ACTION`과 이 실험의 조회·삽입 결과가 같은 `ON DELETE RESTRICT`를 명시했다.

## 비교군

| 이름 | 설명 |
| --- | --- |
| `N_PLUS_ONE` | Block 목록 조회 후 Block마다 Rich Text를 다시 조회한다. |
| `BATCH` | 정규화된 A안 데이터를 set 기반 단일 조회로 조립한다. |
| `SNAPSHOT_GENERATE` | Batch 결과를 `page_render_snapshots`에 생성한다. |
| `SNAPSHOT_READ` | Render Snapshot 한 Row를 읽는다. |
| `COMPACT` | JSONB payload와 `position_key`를 가진 비교 테이블을 읽는다. |
| `INTEGER_REORDER` | 정수 순서를 범위 UPDATE한다. |
| `POSITION_KEY_REORDER` | 이동 Block 한 Row의 문자열 순서 키만 변경한다. |

모든 읽기 비교군은 SHA-256으로 같은 논리 JSON을 반환하는지 검증한다. Snapshot 생성은 조회 개선과 분리해 생성 시간·WAL·저장 크기를 측정한다.

## 데이터와 자원 매트릭스

- 논리 Block: `1,000`, `10,000`, `100,000`
- PostgreSQL hard memory: `1,024MiB`, `512MiB`, `256MiB`
- swap: 비활성화
- PostgreSQL: 18.4
- Rich Text: Block당 3 Segment, 10% Mention
- Raw Snapshot: Page와 모든 Block에 한 건
- 반복: 기본 3회, warm-up 1회
- 동시 순서 변경: 10명, 50명

실제 운영·고객 데이터는 사용하지 않는다. 데이터는 고정 seed와 결정적 SQL로 생성한다.

## 실행

빠른 검증:

```bash
./gradlew performanceTest \
  -Dperformance.sizes=1000 \
  -Dperformance.memoryMiB=512 \
  -Dperformance.iterations=1 \
  -Dperformance.warmups=0 \
  -Dperformance.concurrency=10
```

전체 실험:

```bash
./gradlew performanceTest \
  -Dperformance.sizes=1000,10000,100000 \
  -Dperformance.memoryMiB=1024,512,256 \
  -Dperformance.iterations=3 \
  -Dperformance.warmups=1 \
  -Dperformance.concurrency=10,50
```

결과는 `build/reports/performance/latest`에 생성된다.

```text
latest/
├── environment.json
├── metrics.csv
├── metrics.json
├── summary.md
├── jvm.jfr
└── query-plans/
```

## 판정 기준

- 같은 dataset·seed·메모리 제한에서만 개선율을 계산한다.
- 읽기 시간에는 JDBC 조회, JVM 객체 조립, JSON 생성을 포함한다. Spring/JPA/HTTP/커넥션 풀 비용은 포함하지 않는다.
- `EXPLAIN (ANALYZE, BUFFERS, WAL, FORMAT JSON)`으로 SQL 실행계획을 보존한다.
- JVM heap peak와 PostgreSQL cgroup `memory.current`, `memory.peak`, `memory.events`를 기록한다.
- OOM, timeout, deadlock은 숨기지 않고 결과 행의 관측 상태로 남긴다.
- A안·Snapshot·Compact가 같은 DB에 공존하므로 cgroup OOM은 통합 비교 fixture의 한계다. 모델별 단독 용량 한계는 별도 컨테이너 실험이 필요하다.
- Snapshot은 읽기 시간만이 아니라 생성 시간, 크기, WAL을 함께 평가한다.
- Docker Desktop 결과는 운영 절대 성능이 아니라 같은 환경에서의 상대 비교 근거로 사용한다.

## 2단계 앱·Importer 경로 실험 조건

다음 구현물이 생기면 이 하네스의 합성 fixture를 그대로 재사용한다.

1. Notion API payload를 Knot 저장 모델로 변환하는 Importer 경계
2. 운영 Entity/Repository/Service와 페이지 조회 API
3. 앱 컨테이너와 DB 커넥션 풀 설정

이때 `Notion fixture → Importer → DB → Service/Repository → HTTP JSON`을 측정하고, 현재 JDBC 결과와 비교해 매핑·ORM·직렬화·네트워크 비용을 분리한다. 실제 익명화 fixture가 확보되기 전에는 “Notion DB 전체를 그대로 마이그레이션한 앱 성능”으로 일반화하지 않는다.
