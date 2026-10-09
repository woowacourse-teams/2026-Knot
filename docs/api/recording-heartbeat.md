# 녹음 생존 신호 API

이 문서는 [Issue #384](https://github.com/woowacourse-teams/2026-Knot/issues/384)의 구현 계약이다. 범위는 2026-10-09 Issue 코멘트로 바꾼 내용을 따른다.
새로고침 뒤 수집 자동 재개와 복구 화면은 이 API의 범위가 아니다. 팀 Notion에 반영하거나 승인받은 문서는 아니다.

## 규칙

- 녹음을 시작한 최초 탭이 RECORDING·PAUSED 동안 30초마다 보낸다.
- 서버는 마지막 유효 신호(`lastSeenAt`)부터 120초가 지나면 연결이 끊긴 것으로 본다. 판정은 서버 시각으로만 한다.
- 연결이 끊긴 녹음은 `ENDED`, `endReason=CONNECTION_EXPIRED`, `endedAt = lastSeenAt + 120초`로 확정한다. `lastSeenAt`은 마지막 유효 신호 시각 그대로 둔다.
- 신호·일시정지·재개·종료·시작 재요청·새 시작이 모두 같은 판정을 먼저 한다. 늦게 도착한 요청은 끊긴 녹음을 되살리지 않는다.
- 일반 조회(`GET .../recordings/current`)는 생존 신호가 아니다.

## 요청

`POST /api/v1/workspaces/{workspaceId}/recordings/{recordingId}/heartbeat`

인증 쿠키와 `X-XSRF-TOKEN`을 함께 보낸다. 본문은 일시정지·재개·종료와 같다.

```json
{
  "tabId": "0f8fad5b-d9cb-469f-a165-70867728950e",
  "controlToken": "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefA"
}
```

## 성공 응답 `200 OK`

```json
{
  "recordingId": 10,
  "status": "RECORDING",
  "elapsedMillis": 90000,
  "lastSeenAt": "2026-10-09T10:01:30Z",
  "expiresAt": "2026-10-09T10:03:30Z",
  "endedAt": null,
  "endReason": null,
  "serverNow": "2026-10-09T10:01:30Z"
}
```

| 필드 | 설명 |
| --- | --- |
| `status` | `RECORDING`, `PAUSED`, `ENDED`, `DISCARDED` |
| `elapsedMillis` | 일시정지를 뺀 서버 기준 녹음 시간 |
| `lastSeenAt` | 서버가 받은 마지막 유효 신호 시각 |
| `expiresAt` | 이 시각까지 신호가 없으면 연결 만료로 종료된다. 종료된 녹음은 `null` |
| `endedAt` | 종료 시각. 진행 중이면 `null` |
| `endReason` | `USER_ENDED`, `CONNECTION_EXPIRED`. 진행 중이거나 이 기능 이전에 종료된 녹음은 `null` |
| `serverNow` | 응답을 만든 서버 시각 |

이미 종료된 녹음에 보낸 신호도 `200`이다. 이때는 `lastSeenAt`을 갱신하지 않고 종료 상태를 그대로 돌려준다. FE는 `status`가 `ENDED`이면 수집을 멈춘다.

## 오류

| 상태 | code | 의미 |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR`·`INVALID_REQUEST_BODY` | 본문 누락 또는 형식 오류 |
| 400 | `INVALID_WORKSPACE_ID`·`INVALID_RECORDING_DATA`·`INVALID_PARAMETER` | 잘못된 ID |
| 401 | `UNAUTHENTICATED` | 로그인 필요 |
| 403 | `WORKSPACE_ACCESS_DENIED` | Workspace 멤버가 아님(탈퇴 포함). 종료 상태도 공개하지 않는다 |
| 403 | `RECORDING_CONTROL_DENIED` | 녹음 시작자가 아니거나 최초 탭의 탭 ID·제어 증명이 아님 |
| 403 | `FORBIDDEN` | CSRF 검증 실패 |
| 404 | `WORKSPACE_NOT_FOUND`·`RECORDING_NOT_FOUND` | 없거나 다른 Workspace의 녹음 |

## 다른 API에 생기는 변화

- 일시정지·재개: 연결이 끊긴 녹음이면 만료 종료를 저장한 뒤 `409 RECORDING_ALREADY_ENDED`를 반환한다.
- 종료: 연결이 끊긴 녹음이면 요청 시각이 아니라 `lastSeenAt + 120초`를 `endedAt`으로 반환한다.
- 시작 재요청: 연결이 끊긴 녹음이면 만료를 저장하고 `status=ENDED`인 기존 결과를 반환한다.
- 새 시작: 다른 Workspace에 남은 본인 녹음도 연결이 끊겼다면 회수한 뒤 시작한다. 살아 있으면 기존처럼 `409 ACTIVE_RECORDING_ALREADY_EXISTS`다.

## 이번 범위 밖

- 요청이 없는 녹음을 회수하는 배치와 2시간 상한(#385)
- 녹음 단건 상세 조회(#380)
- 새로고침 뒤 오디오 보존과 수집 재개
