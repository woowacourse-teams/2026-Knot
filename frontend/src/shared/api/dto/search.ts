/**
 * 탐색 DTO
 *
 * - POST /api/v1/workspaces/{workspaceId}/search/conversations (SSE)
 */

/** 답변 준비 단계. SEARCHING은 문서 검색 중, GENERATING은 답변 생성 중 */
export type SearchStreamStage = "SEARCHING" | "GENERATING";

// POST /api/v1/workspaces/{workspaceId}/search/conversations (SSE)

/** 첫 질문 전송 시 앱이 넘기는 값 */
export interface PostSearchConversationRequestInput {
  content: string;
  requestId: string;
}

/** 첫 질문 전송 요청 본문. 응답은 JSON이 아니라 SSE 스트림 */
export class PostSearchConversationRequestDto {
  /** 질문 내용. 앞뒤 공백 제거 */
  content: string;
  /** 같은 질문의 재전송을 서버가 알아보는 키(UUID). 재시도에도 같은 값을 보냄 */
  requestId: string;

  constructor({ content, requestId }: PostSearchConversationRequestInput) {
    this.content = content.trim();
    this.requestId = requestId;
  }
}

/** accepted 전 HTTP 오류의 서버 응답 모양 */
export interface PostSearchConversationErrorResponseRaw {
  code: string;
  message: string;
}

/** accepted 이벤트 전에 실패한 HTTP 오류 응답. 403·409 등이 같은 모양 */
export class PostSearchConversationErrorResponseDto {
  /** 오류 코드. 예: WORKSPACE_ACCESS_DENIED */
  code: string;
  /** 사용자에게 보여 줄 수 있는 오류 메시지 */
  message: string;

  constructor(raw: PostSearchConversationErrorResponseRaw) {
    this.code = raw.code;
    this.message = raw.message;
  }
}

/** `event: accepted`의 data 모양 */
export interface SearchStreamAcceptedRaw {
  conversationId: number;
  questionMessageId: number;
  answerMessageId: number;
}

/** 서버가 질문을 받아 대화와 메시지를 만들었음을 알리는 이벤트. 스트림의 첫 이벤트 */
export class SearchStreamAcceptedDto {
  /** 새로 만들어진 탐색 대화 ID */
  conversationId: number;
  /** 저장된 질문 메시지 ID */
  questionMessageId: number;
  /** 이어서 채워질 답변 메시지 ID. 뒤따르는 이벤트의 answerMessageId와 같음 */
  answerMessageId: number;

  constructor(raw: SearchStreamAcceptedRaw) {
    this.conversationId = raw.conversationId;
    this.questionMessageId = raw.questionMessageId;
    this.answerMessageId = raw.answerMessageId;
  }
}

/** `event: progress`의 data 모양 */
export interface SearchStreamProgressRaw {
  stage: SearchStreamStage;
}

/** 답변 준비 단계가 바뀌었음을 알리는 이벤트 */
export class SearchStreamProgressDto {
  /** 지금 단계. SEARCHING(문서 검색 중) 또는 GENERATING(답변 생성 중) */
  stage: SearchStreamStage;

  constructor(raw: SearchStreamProgressRaw) {
    this.stage = raw.stage;
  }
}

/** `event: delta`의 data 모양 */
export interface SearchStreamDeltaRaw {
  answerMessageId: number;
  text: string;
}

/** 답변 조각. 스트림 하나에서 여러 번 도착함 */
export class SearchStreamDeltaDto {
  /** 이 조각이 속한 답변 메시지 ID */
  answerMessageId: number;
  /** 답변의 일부 텍스트. 도착 순서대로 이어 붙이면 전체 답변이 됨 */
  text: string;

  constructor(raw: SearchStreamDeltaRaw) {
    this.answerMessageId = raw.answerMessageId;
    this.text = raw.text;
  }
}

/**
 * evidence 이벤트의 근거 항목 서버 모양.
 *
 * 임시 가정: API 명세에 items[]의 필드가 아직 정해지지 않아,
 * 메시지 조회 API의 근거 필드(`shared/types/search.ts`)를 따라 둠. 명세가 정해지면 갱신
 */
export interface SearchStreamEvidenceItemRaw {
  documentId: number;
  title: string;
  topic: string;
  rank: number;
}

/** 답변의 근거가 된 문서 하나 (임시 가정 모양, `SearchStreamEvidenceItemRaw` 참고) */
export class SearchStreamEvidenceItemDto {
  /** 근거 문서 ID */
  documentId: number;
  /** 문서 제목 */
  title: string;
  /** 문서 주제 */
  topic: string;
  /** 관련도 순위. 1~3이고 작을수록 관련도가 높음 */
  rank: number;

  constructor(raw: SearchStreamEvidenceItemRaw) {
    this.documentId = raw.documentId;
    this.title = raw.title;
    this.topic = raw.topic;
    this.rank = raw.rank;
  }
}

/** `event: evidence`의 data 모양 */
export interface SearchStreamEvidenceRaw {
  answerMessageId: number;
  items: SearchStreamEvidenceItemRaw[];
}

/** 답변의 근거 문서 목록을 알리는 이벤트 */
export class SearchStreamEvidenceDto {
  /** 근거가 붙는 답변 메시지 ID */
  answerMessageId: number;
  /** 근거 문서. 최대 3개 */
  items: SearchStreamEvidenceItemDto[];

  constructor(raw: SearchStreamEvidenceRaw) {
    this.answerMessageId = raw.answerMessageId;
    this.items = raw.items.map((item) => new SearchStreamEvidenceItemDto(item));
  }
}

/** `event: excluded_documents`의 data 모양 */
export interface SearchStreamExcludedDocumentsRaw {
  count: number;
}

/** 답변 근거에서 제외된 문서 수를 알리는 이벤트 */
export class SearchStreamExcludedDocumentsDto {
  /** 제외된 문서 수 */
  count: number;

  constructor(raw: SearchStreamExcludedDocumentsRaw) {
    this.count = raw.count;
  }
}

/** `event: completed`의 data 모양 */
export interface SearchStreamCompletedRaw {
  answerMessageId: number;
  status: "COMPLETED";
}

/** 답변이 끝나고 저장까지 마쳤음을 알리는 이벤트. 스트림의 마지막 이벤트 */
export class SearchStreamCompletedDto {
  /** 완료된 답변 메시지 ID */
  answerMessageId: number;
  /** 답변 메시지의 최종 상태. 항상 COMPLETED */
  status: "COMPLETED";

  constructor(raw: SearchStreamCompletedRaw) {
    this.answerMessageId = raw.answerMessageId;
    this.status = raw.status;
  }
}

/** `event: failed`의 data 모양 */
export interface SearchStreamFailedRaw {
  answerMessageId: number;
  status: "FAILED";
  code: string;
}

/** accepted 뒤에 답변 생성이 실패했음을 알리는 이벤트. HTTP 상태가 아니라 이벤트로 옴 */
export class SearchStreamFailedDto {
  /** 실패한 답변 메시지 ID */
  answerMessageId: number;
  /** 답변 메시지의 최종 상태. 항상 FAILED */
  status: "FAILED";
  /** 실패 원인 오류 코드. 예: LLM_STREAM_FAILED */
  code: string;

  constructor(raw: SearchStreamFailedRaw) {
    this.answerMessageId = raw.answerMessageId;
    this.status = raw.status;
    this.code = raw.code;
  }
}

/** `event: stopped`의 data 모양 */
export interface SearchStreamStoppedRaw {
  answerMessageId: number;
  status: "STOPPED";
}

/** 답변 생성이 중단됐음을 알리는 이벤트 */
export class SearchStreamStoppedDto {
  /** 중단된 답변 메시지 ID */
  answerMessageId: number;
  /** 답변 메시지의 최종 상태. 항상 STOPPED */
  status: "STOPPED";

  constructor(raw: SearchStreamStoppedRaw) {
    this.answerMessageId = raw.answerMessageId;
    this.status = raw.status;
  }
}

/**
 * 탐색 스트림이 흘려보내는 이벤트 한 건.
 *
 * `event`로 갈라 `data`의 모양이 좁혀지도록 유니언으로 둡니다.
 * 명세에 없는 이벤트 이름은 요청 함수에서 버리므로 여기에 두지 않습니다.
 */
export type SearchStreamEvent =
  | { event: "accepted"; data: SearchStreamAcceptedDto }
  | { event: "progress"; data: SearchStreamProgressDto }
  | { event: "delta"; data: SearchStreamDeltaDto }
  | { event: "evidence"; data: SearchStreamEvidenceDto }
  | { event: "excluded_documents"; data: SearchStreamExcludedDocumentsDto }
  | { event: "completed"; data: SearchStreamCompletedDto }
  | { event: "failed"; data: SearchStreamFailedDto }
  | { event: "stopped"; data: SearchStreamStoppedDto };
