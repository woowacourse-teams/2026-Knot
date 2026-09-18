import type { WorkspacePage, WorkspaceTreeNode } from "../types/workspaceTree";

/**
 * 부모 ID만 들고 있는 평평한 페이지 목록을 사이드바가 그릴 트리로 묶어요.
 *
 * 서버는 트리를 중첩된 모양으로 주지 않고 각 페이지가 부모를 가리키기만 하므로 여기서 묶습니다.
 * 형제끼리는 `position` 순으로 늘어놓고, 문서 수는 그 아래 모든 깊이의 페이지를 셉니다.
 *
 * 부모가 목록에 없는 페이지(권한이 없거나 발행되지 않은 페이지의 자식)는 놓을 자리가 없어 그리지 않습니다.
 */
export const toWorkspaceTree = (pages: WorkspacePage[]) => {
  const childrenByParentId = new Map<number | null, WorkspacePage[]>();

  pages.forEach((page) => {
    const siblings = childrenByParentId.get(page.parentPageId) ?? [];

    childrenByParentId.set(page.parentPageId, [...siblings, page]);
  });

  const toNodes = (parentId: number | null): WorkspaceTreeNode[] =>
    [...(childrenByParentId.get(parentId) ?? [])]
      .sort((a, b) => a.position - b.position)
      .map((page) => {
        const children = toNodes(page.id);

        return {
          id: page.id,
          name: page.title,
          documentCount: children.reduce(
            (count, child) => count + 1 + child.documentCount,
            0,
          ),
          children,
        };
      });

  return toNodes(null);
};
