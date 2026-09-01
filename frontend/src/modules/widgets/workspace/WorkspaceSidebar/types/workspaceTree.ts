export interface WorkspaceFolderNode {
  type: "folder";
  id: string;
  name: string;
  /** 폴더 안 문서 수. 폴더 행 우측에 보여줘요. */
  documentCount: number;
  children: WorkspaceTreeNode[];
}

export interface WorkspaceFileNode {
  type: "file";
  id: string;
  name: string;
}

export type WorkspaceTreeNode = WorkspaceFolderNode | WorkspaceFileNode;
