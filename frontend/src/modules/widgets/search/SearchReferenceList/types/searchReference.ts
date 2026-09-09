/** 출처 문서의 제공자. 화면에서 아이콘을 고르는 키라 소문자예요 */
export type ReferenceSource = "notion";

/** 출처 조회 응답 한 건 중 목록을 만드는 데 필요한 부분. 쿼리 훅의 `data`에서 추론된 모양과 같아요 */
export interface ReferenceChunk {
  /** 메시지 안의 관련도 순위. 1부터 */
  rank: number;
  /** 관련도 점수(0~1) */
  relevanceScore: number;
  /** 서버가 주는 제공자 이름. 대문자(`NOTION`) */
  source: string;
  notionPage: {
    /** 원본 Notion 페이지 ID. 같은 페이지의 청크를 묶는 키 */
    id: string;
    title: string;
    notionUrl: string;
    /** ISO 8601 */
    updatedAt: string;
  };
}

/** 같은 페이지의 청크를 하나로 묶은 목록 항목 */
export interface ReferencePage {
  /** 원본 Notion 페이지 ID */
  id: string;
  title: string;
  href: string;
  /** 페이지의 대표 점수. 묶인 청크 중 가장 높은 점수 */
  score: number;
  /** 묶인 청크 중 가장 앞선 순위. 점수가 같을 때의 정렬 기준 */
  rank: number;
  /** 서버가 주는 제공자 이름 */
  source: string;
  /** ISO 8601 */
  updatedAt: string;
}
