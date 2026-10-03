# #439 초대 수단별 만료 저장 기반

## 범위

#399의 I01이다. 기준은 사용자 확정 취소 없는 MVP와 #439이며, 선행 #411의
`93d02290` 위에 구현한다. 독립 만료의 Domain·저장 기반만 제공한다.
발급·미리보기·수락 HTTP는 기존 공통 만료 계약을 유지한다.

- 상위 #399, 현재 #439, 하위 이슈 없음.
- 신규 Domain 생성 경로와 수단별 유효성 판정은 후속 I02/#402/#403에서 연결한다.
  이 PR만 배포해 다른 TTL로 발급하거나 복수 초대를 공개하지 않는다.
- 구 `expires_at`·고정24시간 CHECK·단일 미무효화 UNIQUE·암호문·재발급은 유지한다.
  소비자 전환과 구 API 차단을 확인한 후 #404에서 제거한다.
- 취소·연장·발급자 복원·운영 DB 수정·FE 변경은 범위 밖이다.

## 호환성과 검증 근거

| 조건 | 근거 |
| --- | --- |
| 기존 유효·만료·무효 행 보존 | V15→V17 upgrade 전후 legacy 전체 컬럼 비교, 두 수단 backfill·효력 검사 |
| 구 INSERT 호환 | nullable 확장 컬럼 생략 INSERT, JPA getter의 기존 expires_at fallback |
| 독립 만료 | Domain 경계 테스트와 서로 다른 시각의 JPA 저장·clear·재조회 |
| 만료 경계·정밀도 | 생성 전·정확한 만료 시각·이후 거절, MICROS 절삭 뒤 양수 기간 검증 |
| 과거 무효 초대 부활 금지 | 두 수단 모두 invalidated_at 차단, 무효 Entity 저장 왕복 |
| DB 불변식 | 수단별 시각 > created_at, 두 컬럼 쌍, FK RESTRICT 실제 실패와 행 보존 |
| 구 API·동시성 보호 | 기존 단수 UNIQUE·hash UNIQUE·낙관적 잠금·발급/수락 테스트 유지 |

확장 컬럼의 null은 구 버전 쓰기 호환을 위한 임시 상태다. 기존 행은 두 컬럼 모두
backfill하며 새 Domain은 둘 다 명시적으로 저장한다. 한쪽만 있는 상태는 DB가 거부한다.
HTTP에서 사용하는 기존 isValidAt은 공통 expires_at을 계속 사용하므로 독립 만료 API가
완료됐다는 뜻이 아니다. 테스트용 직접 생성과 공개 기능 전환을 구분한다.

## Migration과 배포 경계

V17만 추가하고 기존 적용본은 수정하지 않았다. 최신 develop에는 V15 인증 세션과
V16 회원 삭제가 있어 V17을 선택했다. 선행 #411의 V15 Workspace 이력과 develop V15의
번호 충돌은 선행 통합 때 실제 적용 이력을 확인해 해결해야 한다. 이 PR의 스택 CI는
develop 통합·운영 upgrade를 증명하지 않는다. 운영 DB 이력·행은 조회하지 않았다.

구 버전으로 애플리케이션만 되돌려도 추가 컬럼을 생략한 INSERT는 허용된다.
독립 만료 행을 공개한 뒤 구 공통 만료 API로 롤백하는 것은 지원하지 않으므로,
해당 공개는 I02/I09 전환 및 후속 수단별 소비자 검증과 함께 수행한다.

## 적용 장부

| 규칙군 | 적용 |
| --- | --- |
| STACK/LIB | Java25·기존 Spring/JPA/PostgreSQL18, 신규 의존성 없음 |
| LAYER/MODEL | Entity 불변식·JPA 컬럼 매핑, 기존 Repository·HTTP 책임 유지 |
| API | 공개 계약 유지, 신규 Domain 오류는 기존 ErrorCategory·한글 메시지 |
| DB | 추가 migration·RESTRICT·CHECK, Testcontainers·Flyway·Hibernate validate |
| JAVA | 기존 Getter 관례·MICROS·120자·필드 블록·인자 줄바꿈 |
| TEST | Domain 경계와 실제 PostgreSQL, GWT 단일 행위, 기존 인수 회귀 |
| AI | 독립 검토와 작성자 검토, 테스트 수정·검증 결과로 판단 |
| DELIVERY | #399 하위 #439, 선행 스택 base, 문서·삭제 포함 1000줄 이내 |

Issue 계약 `c34332f46b636a16`은 Pass, 자료 충분으로 인터뷰 생략이다.
상위 결정 복구 계약 `c224b6c95b79cd4f`로 ADR399를 Proposed로 생성했다.
로컬 00~08 원문 hash와 사용자 승인 Java/API 규칙을 적용했으며 팀 Notion 실시간
동기화와 팀 승인 사실을 주장하지 않는다.

## 검증 결과

- `./gradlew spotlessApply test integrationTest acceptanceTest bootJar --no-parallel`:
  단위 229·통합 93·인수 128건 통과, 실패·오류·건너뜀 0건. 새 DB 검증 10건을 포함한다.
- Governance 단위 8건, 원문 hash·변경 파일 컨벤션 감사 통과.
- 기본 검증 wrapper의 Spotless·단위 검증 통과. 독립 검토에서 migration·DB 테스트의
  차단 결함은 없었으며 작성자가 도메인과 전체 diff를 검토했다.
- 전체 감사·full wrapper는 범위 밖 기존 위반 5건에서 중단했다.
  JAVA-FIELD-ONE 2건, LAYER-PRESENTATION-DEPENDENCY 2건, DB-FK-DELETE 1건이며
  전체 Gradle 검증은 별도로 실행해 통과했다.
- 최초 실행에서 fixture의 Workspace created_at 누락을 수정했다. PostgreSQL18의
  RESTRICT 실패는 SQLSTATE 23001임을 확인해 기대값을 바로잡았다. 기존 upgrade 테스트
  3건의 최신 버전·실행 개수 기대값을 V17에 맞추고 전체 재검증했다.
