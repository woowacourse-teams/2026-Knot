import { useState } from "react";

import { INITIAL_EXPANDED_FOLDER_IDS } from "../constants/workspaceSidebar";

/**
 * 사이드바 폴더 트리의 펼침/접힘 상태를 관리해요.
 *
 * 사이드바가 닫혀도 컴포넌트는 남아 있으므로 다시 열었을 때 펼침 상태가 유지돼요.
 */
export const useWorkspaceTree = () => {
  const [expandedFolderIds, setExpandedFolderIds] = useState(
    () => new Set(INITIAL_EXPANDED_FOLDER_IDS),
  );

  const isFolderExpanded = (folderId: string) =>
    expandedFolderIds.has(folderId);

  const toggleFolder = (folderId: string) => {
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
