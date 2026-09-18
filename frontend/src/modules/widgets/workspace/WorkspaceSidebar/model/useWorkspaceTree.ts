import { useState } from "react";

/**
 * 사이드바 폴더 트리의 펼침/접힘 상태를 관리해요.
 *
 * 처음에는 모두 접어 두고, 사용자가 연 폴더만 펼쳐 둡니다.
 * 사이드바가 닫혀도 컴포넌트는 남아 있으므로 다시 열었을 때 펼침 상태가 유지돼요.
 */
export const useWorkspaceTree = () => {
  const [expandedFolderIds, setExpandedFolderIds] = useState(
    () => new Set<number>(),
  );

  const isFolderExpanded = (folderId: number) =>
    expandedFolderIds.has(folderId);

  const toggleFolder = (folderId: number) => {
    setExpandedFolderIds((prev) => {
      const next = new Set(prev);

      if (next.has(folderId)) {
        next.delete(folderId);
      } else {
        next.add(folderId);
      }

      return next;
    });
  };

  return { isFolderExpanded, toggleFolder };
};
