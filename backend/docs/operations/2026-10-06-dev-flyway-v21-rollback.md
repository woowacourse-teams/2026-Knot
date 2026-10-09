# Dev 반복 배포 롤백과 Flyway V21 누락 복구

발생 및 복구일: 2026-10-06 · 시각 기준: KST · 대상: AWS Dev Spring API

Dev 배포가 반복해서 롤백되면서 최신 API가 Swagger에 나타나지 않았다. 원인은 Spring
기동 시간이 아니라 **DB에 V22와 V23이 적용된 상태에서 V21이 누락된 것**이었다.
Flyway가 시작 중 검증에 실패해 앱이 종료됐고, CodeDeploy의 health 검사 실패가
자동 롤백을 유발했다. 누락된 V21만 제한적으로 적용한 뒤 정상 파이프라인으로 재배포해
최신 커밋의 배포 성공과 Swagger 반영을 확인했다.

## 문제 상황

### 사용자에게 보인 증상

- Dev 파이프라인의 실패와 자동 롤백이 반복됐다.
- 롤백 실행에는 `성공`이 표시됐지만, 최신 기능이 아니라 이전 커밋이 복원됐다.
- [Dev Swagger](https://dev-api.knoted.kr/swagger-ui/index.html)에 현재 녹음 조회와
  최종 오디오 업로드 완료 확인 API가 보이지 않았다. 복구 전 OpenAPI 경로는 15개였다.

실패한 최신 배포의 소스는 `1f0d6bee`였고, 자동 롤백은 이전 성공 소스인 `b5f066be`를
복원했다. 따라서 **롤백 성공과 최신 커밋 배포 성공을 구분**해야 했다.

### 원인을 좁힌 근거

| 확인 대상 | 관측 결과 | 해석 |
| --- | --- | --- |
| CodeBuild | 빌드 성공 | 컴파일 단계보다 이후 배포 및 앱 기동 확인이 필요 |
| Spring journal | 아래 Flyway 검증 예외로 앱 종료 | health 대기 시간을 늘리는 것만으로 해결되지 않음 |
| CodeDeploy ValidateService | health 검사 실패 후 exit 1 | 앱 기동 실패가 배포 실패와 롤백으로 이어짐 |
| 실제 Dev DB 이력 | V22·V23 성공, V21 없음 | 최신 산출물의 migration과 DB 이력 불일치 |
| 실제 Dev DB 테이블 | `auth_sessions` 존재, `auth_session_consumed_refresh_tokens` 없음 | V21이 이미 실행됐는데 기록만 빠진 상태가 아님 |

실제 앱 로그의 핵심 오류는 다음과 같았다.

```text
FlywayValidateException: Validate failed: Migrations have failed validation
Detected resolved migration not applied to database: 21.
```

Flyway는 SQL migration과 DB의 적용 이력을 대조하고 필요한 변경을 적용한다.
이번에는 이미 적용된 V23보다 낮은 V21이 뒤늦게 배포 산출물에 포함됐지만,
순서가 뒤바뀐 migration 적용이 허용되지 않아 검증 단계에서 멈췄다.

```text
빌드 성공 → Flyway 검증 실패 → Spring 종료
→ CodeDeploy health 검사 실패 → 자동 롤백 → 이전 Swagger 유지
```

`scripts/validate_service.sh`는 health를 최대 30회 검사하고 실패할 때마다 2초씩
기다린다. 약 60초의 재시도 대기이지만 curl 실행 시간까지 포함한 엄밀한 전체 제한은
아니다. 이번 실패는 약 14초 만의 앱 종료였고, 복구 후 기동은 16.803초였으므로
기동 지연 가설을 뒷받침하지 않았다.

V21이 어떤 merge나 배포 순서 때문에 빠졌는지까지는 확정하지 않았다.
확인된 직접 원인은 **실제 Dev DB의 V21 미적용과 그로 인한 검증 실패**다.

## 대안

아래 대안은 이번 대응에서 검토한 선택지다. 채택하지 않은 방법을 운영에 적용해
시험한 것은 아니다.

| 대안 | 기대 효과 | 판단 |
| --- | --- | --- |
| health 검사 대기 시간 확대 | 정상 기동이 오래 걸리는 앱에 준비 시간 제공 | 검증 예외로 종료하는 이번 원인을 해결하지 못하므로 변경하지 않음 |
| 기존 성공 버전 유지 | 기존 API를 복구 | 서비스 복구 수단이지만 최신 기능은 계속 미반영되므로 최종 해결책으로 삼지 않음 |
| validation 비활성화 또는 이력만 보정 | 시작을 진행시키거나 검증 상태 변경 | 실제 테이블이 없는 문제를 해결하지 못하고 불일치를 숨기므로 사용하지 않음 |
| `outOfOrder` 상시 활성화 | 낮은 버전 migration의 뒤늦은 적용 허용 | 이후 배포에도 영향을 주므로 영구 설정으로 적용하지 않음 |
| V21만 대상으로 제한적 순서 외 적용 | 누락 SQL을 실제 실행하고 적용 이력 생성 | Dev DB와 선행 테이블·이력·대상 SQL을 확인한 뒤 채택 |

## 결정

**Dev DB에 누락된 V21만 한 번 적용하고, 최신 앱 배포는 기존 자동 파이프라인으로
수행했다.** 제품 코드나 health 검사 대기 시간은 변경하지 않았다.

### 복구 범위 제한

실패한 Dev 배포 산출물에 포함된 Flyway 12.4.0과 migration SQL을 사용했다.
임시 복구 도구는 다음 조건을 검사한 뒤 실행했다.

1. 연결 대상이 지정된 Dev RDS이고 DB 이름이 `knot`인지 확인했다.
2. V21 이력과 대상 테이블이 모두 없고, 선행 테이블 `auth_sessions`가 있는지 확인했다.
3. 적용 대상이 V21 하나인지 검사했다.
4. 복구 프로세스에서만 `target=21`, `outOfOrder=true`, `cleanDisabled=true`를 사용했다.

V21은 소비된 refresh token의 해시를 저장하는 `auth_session_consumed_refresh_tokens`
테이블과 제약·인덱스를 생성한다. 기존 `auth_sessions`를 참조하므로 실제 테이블 상태와
SQL을 먼저 대조했다. DB 초기화, validation 비활성화, 기존 migration 파일 수정이나
이력 위조는 하지 않았다.

적용 전 Flyway 이력은 서버의 root 전용 백업 디렉터리에 보존했다. **이 백업은 적용
이력 TSV이며 전체 DB 백업이 아니다.** 자격 증명은 서버 내부에서만 사용했고 문서에
기록하지 않았다. 복구 후 임시 도구와 산출물 추출본은 삭제했다.

### 정상 파이프라인 재배포

과거 실패 실행의 재시도는 뒤이은 실행으로 대체돼 허용되지 않았다. 대신 정상
[Dev 파이프라인](https://ap-northeast-2.console.aws.amazon.com/codesuite/codepipeline/pipelines/knot-backend-dev-pipeline/view?region=ap-northeast-2)의
변경 사항 릴리스를 실행했다. 수동 JAR 교체로 배포 성공을 대신하지 않았다.

Prod 서비스·DB와 영구 `outOfOrder` 설정은 변경하지 않았다.

## 확인

### 복구 타임라인

| 시각 | 작업과 결과 |
| --- | --- |
| 복구 전 | 빌드 성공, Spring의 V21 검증 실패, health 검사 실패 및 이전 커밋 자동 롤백 확인 |
| 17:09 | 누락 V21만 적용. `migrationsExecuted=1`, `success=true`; 이력과 테이블 존재 재확인 |
| 17:11경 | 최신 develop 커밋으로 정상 Dev 파이프라인 시작 |
| 17:17:57 | 앱 기동 중 추가 migration 2개 적용. DB schema version은 V26 |
| 17:18:05 | Spring 기동 완료. `Started KnotApplication in 16.803 seconds` |
| 배포 완료 후 | 동일 파이프라인 실행에서 Source·Build·Deploy 성공, 외부 health와 Swagger 반영 확인 |

### 배포 추적 정보

| 항목 | 확인값 |
| --- | --- |
| Pipeline | `knot-backend-dev-pipeline` |
| 성공 실행 | `8568144f-195f-408b-8cb1-53397dc2a9e2` |
| 배포 소스 | `1f0d6bee58a06e622923f6b810d07495ce5c1ec5` |
| CodeBuild 실행 | `knot-backend-dev-build:f1f7cf5b-7065-4cda-8c64-515bed19b968` |
| CodeDeploy 배포 | `d-95BBH87X8` |

### 재확인할 때 볼 위치

- **AWS 실행 내역:** 최신 소스와 Source·Build·Deploy의 실행 ID가 같은지 확인한다.
  `AutomatedRollback` 실행의 성공을 최신 배포 성공으로 해석하지 않는다.
- **CodeDeploy:** 실패한 lifecycle event와 hook 로그를 확인한다. `ValidateService`
  실패라면 앱 로그에서 그보다 앞서 발생한 오류를 찾는다.
- **앱 기동:** Dev 호스트에서 `journalctl -u knot-backend.service`로 해당 배포 시각의
  Flyway 오류와 `Started KnotApplication`을 확인한다.
- **DB:** `flyway_schema_history`의 version·script·success와 실제 테이블을 함께 본다.
  가장 높은 version만 보고 중간 migration 누락을 판단하지 않는다.
- **배포 반영:** [Dev health](https://dev-api.knoted.kr/actuator/health),
  [OpenAPI](https://dev-api.knoted.kr/v3/api-docs),
  [Swagger](https://dev-api.knoted.kr/swagger-ui/index.html)를 확인한다.
  health 정상만으로 최신 API가 반영됐다고 판단하지 않는다.

동일 오류가 재발해도 이 복구를 그대로 반복하지 않는다. 먼저 현재 이력·테이블과
새 migration의 선행 조건을 다시 확인한다. 이미 존재하는 테이블에 CREATE를 재실행하면
별도의 실패를 만들 수 있다.

## 검증 결과

| 검증 항목 | 복구 전 | 복구 후 |
| --- | --- | --- |
| V21 적용 이력과 테이블 | 모두 없음 | V21 `success=true`, 테이블 존재 |
| 추가 migration | 최신 앱 기동 전에 검증 실패 | V24·V26 적용 이력 `success=true` |
| Spring 기동 | 검증 예외로 종료 | 16.803초에 완료 |
| systemd | 실패 후 재시작 반복 | `active/running`, `NRestarts=0`, `ExecMainStatus=0` |
| 최신 커밋 배포 | 실패 후 이전 커밋으로 롤백 | 동일 실행의 Source·Build·Deploy 모두 성공 |
| 외부 health | 최신 앱 준비 실패 | `status=UP` |
| OpenAPI 경로 수 | 15개 | 22개 |
| 현재 녹음 조회 | Swagger 미반영 | `GET /api/v1/workspaces/{workspaceId}/recordings/current` 표시 |
| 오디오 업로드 완료 확인 | Swagger 미반영 | `POST /api/v1/workspaces/{workspaceId}/recordings/{recordingId}/audio-upload-complete` 표시 |

위 결과는 복구 직후 실제 호스트·DB·외부 API와 브라우저에서 확인한 값이다.
API의 배포 및 문서 반영을 검증한 것이며, 인증된 녹음 업무 시나리오와 S3 실제 업로드
완료까지 검증한 결과는 아니다. 이후 배포의 장기 무재발도 별도 관찰이 필요하다.

### 재발 방지 과제

복구는 완료했으며 아래 예방 조치는 아직 구현하지 않았다.

- migration 번호와 병렬 작업의 merge 순서를 배포 전에 점검한다.
- 빈 DB 검증뿐 아니라 이전 릴리스가 적용된 DB의 업그레이드 검증을 추가한다.
- 배포 실패 알림에 실패 단계·소스 커밋·핵심 앱 오류·롤백 여부를 함께 표시한다.

이번 대응으로 빌드 실패, 앱 기동 실패, health 검사 실패와 자동 롤백을 구분할 수 있게
됐다. 다음 유사 장애에서는 대기 시간부터 변경하기보다 **최초 앱 오류와 DB 적용 이력**을
먼저 확인한다.

관련 기록: [스프린트 1 운영 관찰과 대응](sprint-1-observability.md)
