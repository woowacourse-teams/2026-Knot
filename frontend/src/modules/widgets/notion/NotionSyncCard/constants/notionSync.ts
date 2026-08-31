// TODO(동기화 API Issue 미정): Notion 동기화 API 연결 후 지연·안내 문구·문서 수를 응답으로 교체

/** 임시 동기화 지연(ms). 로딩 상태를 눈으로 확인할 만큼만 기다려요. */
export const SYNC_DELAY_MS = 1000;

/** 마지막 동기화 시각 안내. Figma 예시 문구를 그대로 둔 임시 값이에요. */
export const LAST_SYNCED_AT_LABEL = "어제 오후 3:12에 동기화";

/** 동기화가 끝났을 때 새로 들어온 문서 수. */
export const SYNCED_DOCUMENT_COUNT = 8;
