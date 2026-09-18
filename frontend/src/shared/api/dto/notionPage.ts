/**
 * Notion Page DTO
 *
 * - GET /api/v1/workspaces/{workspaceId}/notion-pages/tree
 */

/** Notion Page Tree 항목의 서버 응답 모양 */
export interface NotionPageTreeItemRaw {
  id: number;
  parentPageId: number | null;
  title: string;
  position: number;
  notionUrl: string;
}

/** Notion Page Tree 항목. 부모를 가리키기만 하는 평평한 항목이라 트리 모양은 쓰는 쪽에서 만듦 */
export class NotionPageTreeItemDto {
  /** Knot Page ID */
  id: number;
  /** 부모 Page ID. 최상위 Page면 null */
  parentPageId: number | null;
  /** Page 제목 */
  title: string;
  /** 같은 부모 아래에서의 순서 */
  position: number;
  /** 원본 Notion Page URL */
  notionUrl: string;

  constructor(raw: NotionPageTreeItemRaw) {
    this.id = raw.id;
    this.parentPageId = raw.parentPageId;
    this.title = raw.title;
    this.position = raw.position;
    this.notionUrl = raw.notionUrl;
  }
}

// GET /api/v1/workspaces/{workspaceId}/notion-pages/tree

/** Page Tree 조회의 서버 응답 모양. 본문이 배열 하나 */
export type GetNotionPageTreeResponseRaw = NotionPageTreeItemRaw[];

/** 워크스페이스의 Notion Page Tree 조회 응답. 서버 배열을 `pages` 필드에 담음 */
export class GetNotionPageTreeResponseDto {
  /** 발행된 Page 목록. 없으면 빈 배열 */
  pages: NotionPageTreeItemDto[];

  constructor(raw: GetNotionPageTreeResponseRaw) {
    this.pages = raw.map((page) => new NotionPageTreeItemDto(page));
  }
}
