/** Page Tree 조회 응답의 항목. 부모 ID로만 이어진 평평한 목록이에요 */
export interface NotionPageTreeItem {
  id: number;
  /** 최상위 Page면 null */
  parentPageId: number | null;
  title: string;
  /** 같은 부모 아래에서의 순서 */
  position: number;
  notionUrl: string;
}
