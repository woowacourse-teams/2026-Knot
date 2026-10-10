# 탐색 대화 메시지 조회 API

`GET /api/v1/workspaces/{workspaceId}/search/conversations/{conversationId}/messages`

현재 Workspace의 활성 멤버인 대화 소유자가 저장된 질문·답변·근거를 조회한다.
브라우저는 `credentials: "include"`를 사용한다. GET은 CSRF 헤더를 요구하지 않는다.
본문은 전체 문자열을 그대로 반환하며 생성 중인 부분 답변과 빈 문자열도 포함한다.

구현 근거는 #565의 Controller·DTO·Security·PostgreSQL 인수 테스트다.
실제 AI 생성·SSE 연결·운영 배포 완료를 뜻하지 않는다.

## Header

| Key | Type | Required | Example | Description |
| --- | --- | --- | --- | --- |
| Cookie | String | Yes | `__Host-KNOT_ACCESS_TOKEN=…` | 로그인 access 쿠키. 로컬 HTTP 설정·테스트는 `KNOT_ACCESS_TOKEN`. 브라우저가 자동 전송한다. |

## Path Parameter

| Key | Type | Required | Example | Description |
| --- | --- | --- | --- | --- |
| workspaceId | Long | Yes | 12 | 현재 Workspace ID. 1 이상 |
| conversationId | Long | Yes | 301 | 본인 SearchConversation ID. 1 이상 |

## Query Parameter

| Key | Type | Required | Example | Description |
| --- | --- | --- | --- | --- |
| beforeSequence | Integer | No | 6 | 이 값보다 작은 sequence의 메시지 조회. 첫 요청 생략. 1 이상 |
| size | Integer | No | 30 | 메시지 수. 기본 30, 최소 1, 최대 100 |

Integer 범위는 2,147,483,647까지다. 숫자가 아닌 값이나 범위를 넘는 값은 400이다.
size가 홀수이거나 1이면 질문·답변 쌍이 페이지 경계에서 나뉠 수 있다.

## Request Body

| Field | Type | Required | Nullable | Description | Example |
| --- | --- | --- | --- | --- | --- |
| 없음 | - | - | - | 요청 본문 없음 | - |

### Request Example

`GET /api/v1/workspaces/12/search/conversations/301/messages?beforeSequence=6&size=3`

## Response

### Status Code

| Status | Description |
| --- | --- |
| 200 | 메시지 조회 성공. 이전 메시지가 없으면 빈 배열 |
| 400 | 경로·페이지 입력 형식 또는 범위 오류 |
| 401 | 유효한 access 인증 없음 |
| 403 | 현재 활성 멤버가 아니거나 대화의 Workspace·소유자 불일치 |
| 404 | 멤버십 확인 후 대화가 존재하지 않음 |

### Response Body

| Field | Type | Nullable | Description | Example |
| --- | --- | --- | --- | --- |
| conversationId | Long | No | 조회 대화 ID | 301 |
| items | Array | No | 최신 size개를 선택해 sequence 오름차순으로 반환 | 아래 예시 |
| items[].id | Long | No | SearchMessage ID | 602 |
| items[].role | String | No | USER 또는 ASSISTANT | ASSISTANT |
| items[].sequence | Integer | No | 대화 내 메시지 순서 | 2 |
| items[].content | String | No | 원본 전체 본문. 공백·개행·이모지 보존, STREAMING은 빈 문자열 허용 | 문서 기준을 정리하면… |
| items[].status | String | No | USER는 RECEIVED, ASSISTANT는 STREAMING/COMPLETED/FAILED/STOPPED | STREAMING |
| items[].createdAt | Instant | No | 메시지 저장 시각 | 2026-10-11T00:00:00.123456Z |
| items[].evidences | Array | No | 저장된 유효 Document 카드. 최대 3개, rank 오름차순. USER는 [] | 아래 예시 |
| items[].evidences[].documentId | Long | No | 근거 Document ID | 81 |
| items[].evidences[].title | String | No | 현재 문서 제목 | 문서 보관 정책 |
| items[].evidences[].topic | String | No | 현재 문서 주제 | 문서 정책 |
| items[].evidences[].rank | Integer | No | 관련도 순서 1~3 | 1 |
| hasPrevious | Boolean | No | 이번 페이지보다 앞선 메시지 존재 여부 | false |
| previousCursor | String | Yes | 이전 페이지가 있으면 반환한 최소 sequence의 십진 문자열. 마지막 페이지면 null | null |

### Response Example

```json
{
  "conversationId": 301,
  "items": [
    {
      "id": 601,
      "role": "USER",
      "sequence": 1,
      "content": "문서 보관 기준이 뭐야?",
      "status": "RECEIVED",
      "createdAt": "2026-10-11T00:00:00.123456Z",
      "evidences": []
    },
    {
      "id": 602,
      "role": "ASSISTANT",
      "sequence": 2,
      "content": "문서 기준을 정리하면…",
      "status": "STREAMING",
      "createdAt": "2026-10-11T00:00:00.123456Z",
      "evidences": [
        {"documentId": 81, "title": "문서 보관 정책", "topic": "문서 정책", "rank": 1}
      ]
    }
  ],
  "hasPrevious": false,
  "previousCursor": null
}
```

sequence 1..8에서 size=3이면 [6,7,8]과 previousCursor="6"을 받는다.
이 문자열을 정수로 변환해 `beforeSequence=6`으로 요청하면 [3,4,5]를 받는다.
경계 메시지는 다음 페이지에 포함하지 않는다. beforeSequence=1이면 []·false·null이다.

## Error Response

| Status | Error Code | Description |
| --- | --- | --- |
| 400 | INVALID_PARAMETER | 경로·size·beforeSequence 형식 또는 범위 오류 |
| 401 | UNAUTHENTICATED | 쿠키 없음, 잘못된·만료된 access token, 닉네임 용도 token |
| 403 | SEARCH_ACCESS_DENIED | 탈퇴한 멤버, 삭제된 Workspace, 다른 Workspace·소유자의 대화 |
| 404 | CONVERSATION_NOT_FOUND | 현재 멤버십이 유효하지만 대화 ID가 없음 |

```json
{"code": "CONVERSATION_NOT_FOUND", "message": "탐색 대화를 찾을 수 없습니다"}
```

공통 오류 응답의 fieldErrors는 비어 있으면 생략된다.

## 조회 규칙

- 목록에서 숨긴 대화도 현재 소유자는 ID로 조회할 수 있다. 화면 이탈을 판별하는 계약은 없다.
- GET은 본문·상태·목록 노출·최근 활동·Document 확인 기록을 변경하지 않는다.
- 한 요청의 권한·본문·상태·근거는 같은 DB snapshot을 읽는다. 다음 요청은 이후에 저장된 결과를 읽을 수 있다.
- 근거는 같은 Workspace의 Document 카드만 포함한다. Transcript 원문과 인용문은 반환하지 않는다.
- 저장된 근거를 답변 status에 따라 필터링하지 않는다. STOPPED·FAILED 생성 시점의 근거 저장 정책은 후속 생성 작업에서 다룬다.
- 색인 제외 문서 수는 이 응답의 필드가 아니다.
- STREAMING 복구 이후 실시간 출력은 별도 답변 events API로 연결한다. 이 GET은 생성을 시작하거나 구독하지 않는다.
