/** 트리로 묶기 전의 평평한 페이지. Page Tree 조회 응답 항목의 모양이에요 */
export interface WorkspacePage {
  id: number;
  /** 부모 페이지 ID. 최상위 페이지면 null */
  parentPageId: number | null;
  title: string;
  /** 같은 부모 아래에서의 순서 */
  position: number;
}

/** 사이드바가 그리는 트리의 한 항목 */
export interface WorkspaceTreeNode {
  id: number;
  name: string;
  /**
   * 이 아래에 딸린 문서 수(깊이 상관없이 모두).
   *
   * 하위가 없으면 0이고, 그때는 폴더가 아니라 문서 행으로 그려요.
   */
  documentCount: number;
  children: WorkspaceTreeNode[];
}
