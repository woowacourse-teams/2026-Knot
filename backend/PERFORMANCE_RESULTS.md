# Notion 저장 모델 성능 실험 결과

## 결론

현재 A안 DDL의 Page/Block/Rich Text/Mention/Snapshot 수직 슬라이스에서 N+1 조회와 정수 범위 재정렬은 데이터 증가에 따라 명확한 병목을 만들었다. 반면 set 기반 Batch 조회만으로 조회 병목 대부분이 해소되었으며, Render Snapshot과 Compact JSONB는 이 fixture에서 Batch보다 항상 빠르지 않았다.

- N+1은 10만 Block에서 p50 `24.612초`, SQL `100,001회`였다.
- Batch는 같은 논리 JSON을 p50 `0.274초`, SQL `1회`로 반환해 N+1 대비 `98.9%` 단축했다.
- Snapshot Read는 10만 Block에서 p50 `0.292초`로 Batch보다 `6.6%` 느렸다. Snapshot 생성은 `0.863초`, SQL 2회, WAL 약 `2.50MB`가 추가됐다.
- Compact JSONB는 10만 Block에서 p50 `0.276초`로 Batch와 사실상 비슷했고 `0.8%` 느렸다. 이 결과만으로 Rich Text 정규화 테이블 삭제를 정당화할 수 없다.
- 정수 범위 재정렬은 10만 Block에서 50,000행과 WAL 약 `51.9MB`를 만들었다. 한 Row 순서 키 변경은 1행과 WAL 약 `475KB`였다.
- 10만 Block 동시 정수 재정렬은 동시성 10에서 7/10, 동시성 50에서 48/50 요청이 5초 lock timeout 안에 실패했다. 순서 키 방식은 두 조건에서 모두 실패 0건이었다.
- PostgreSQL 1GiB 제한에서는 10만 Block 통합 비교 실험을 완료했지만 `memory.events max=10,180`으로 한계 압박이 있었다. 512MiB와 256MiB에서는 A안·Snapshot·Compact를 함께 만드는 시드 중 `oom_kill=1`로 종료됐다. 이 결과는 컨테이너 제한 작동 증거지만 A안 단독 용량 한계는 아니다.

## 대표 측정값

아래 값은 PostgreSQL 1GiB, warm-up 1회, 본 측정 3회의 p50이다.

| 논리 Block | N+1 | Batch | Snapshot Read | Compact JSONB | N+1 → Batch |
| ---: | ---: | ---: | ---: | ---: | ---: |
| 1,000 | 227.866ms | 4.347ms | 5.782ms | 5.338ms | 98.1% 단축 |
| 10,000 | 2,176.988ms | 31.606ms | 22.140ms | 33.653ms | 98.5% 단축 |
| 100,000 | 24,611.515ms | 273.931ms | 291.989ms | 276.043ms | 98.9% 단축 |

| 논리 Block | 정수 범위 재정렬 | 순서 키 한 Row | 변경 행 | WAL |
| ---: | ---: | ---: | ---: | ---: |
| 1,000 | 14.475ms | 2.575ms | 500 → 1 | 469,568B → 5,192B |
| 10,000 | 99.803ms | 24.475ms | 5,000 → 1 | 4,739,632B → 47,856B |
| 100,000 | 1,010.721ms | 108.803ms | 50,000 → 1 | 51,933,584B → 474,560B |

## 판정

### 지금 해결해야 하는 것

1. 서버가 Block마다 Rich Text를 재조회하는 N+1 구현을 금지한다.
2. 정규화 모델을 유지하더라도 한 번의 set 기반 조회 또는 제한된 수의 Bulk 조회로 문서를 조립한다.
3. Knot 편집기의 양방향 순서 변경을 고려해 정수 범위 갱신 대신 한 Row만 바꾸는 순서 키를 설계한다.
4. 10만 Block 통합 비교 fixture는 PostgreSQL 1GiB에서도 메모리 압박이 크므로, 저장 모델별 독립 컨테이너와 실제 앱 경로에서 문서 크기 제한·페이지네이션·lazy fetch 경계를 재측정한다.

### 이번 실험으로 확정할 수 없는 것

1. A안의 모든 48개 테이블을 실제 Notion DB 분포대로 채운 결과가 아니다. 실험 스키마를 포함해 49개 테이블을 생성했지만 Page/Block/Rich Text/Mention/Snapshot 중심의 합성 수직 슬라이스만 채웠다. 원본에서 삭제 정책을 생략한 두 FK에는 프로젝트 규칙상 `ON DELETE RESTRICT`를 명시했으며 조회·삽입 실험에는 영향을 주지 않는다.
2. `SEED_LOAD`는 합성 SQL 시드 비용이다. Notion API 호출, Importer 매핑, 재시도, 멱등 저장 성능이 아니다.
3. Spring Entity/Repository/Service/API가 아직 없어 JPA, 커넥션 풀, HTTP 직렬화와 앱 컨테이너 메모리는 측정하지 않았다.
4. 모든 Block이 한 Page의 직계 자식인 wide-tree fixture다. deep-tree lazy fetch 성능은 별도 fixture가 필요하다.
5. 로컬 Docker Desktop 결과는 운영 환경의 절대 SLA가 아니라 같은 환경에서 대안을 비교하는 근거다.
6. A안, Snapshot과 Compact 비교 데이터가 같은 DB에 공존한다. 512/256MiB OOM을 A안 단독 저장 용량 문제로 일반화할 수 없다.

## 후속 실험

운영 Importer와 조회 API가 구현되면 같은 결정적 fixture를 `Notion payload → Importer → PostgreSQL → Repository/Service → HTTP JSON` 경로로 통과시킨다. DB 하네스 수치와 비교해 Import 매핑, ORM, 커넥션 풀, 앱 객체 조립, 직렬화 비용을 분리하고 앱 컨테이너에도 256/512/1024MiB 제한을 적용한다. 실제 익명화 Notion fixture가 확보되면 block-heavy, property-heavy, relation-heavy, mixed workspace 프로필을 추가한다.

## 재현

실험 계획과 명령은 `PERFORMANCE_EXPERIMENT.md`에 있다. 원시 결과는 실행 후 `build/reports/performance/latest`의 `metrics.csv`, `metrics.json`, `jvm.jfr`, `query-plans/`에 생성된다.

최종 실행 조건:

```bash
./gradlew performanceTest \
  -Dperformance.sizes=1000,10000,100000 \
  -Dperformance.memoryMiB=1024,512,256 \
  -Dperformance.iterations=3 \
  -Dperformance.warmups=1 \
  -Dperformance.concurrency=10,50
```
