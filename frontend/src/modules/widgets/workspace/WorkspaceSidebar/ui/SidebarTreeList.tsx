import { css } from "@emotion/react";
import styled from "@emotion/styled";

import type { WorkspaceTreeNode } from "../types/workspaceTree";
import SidebarFileRow from "./SidebarFileRow";
import SidebarFolderRow from "./SidebarFolderRow";

interface SidebarTreeListProps {
  nodes: WorkspaceTreeNode[];
  /** 이 목록에 속한 행의 깊이. 최상위는 0이에요. */
  depth: number;
  isFolderExpanded: (folderId: string) => boolean;
  onToggleFolder: (folderId: string) => void;
}

/**
 * 폴더·문서 행을 깊이만큼 들여써서 나열하는 재귀 목록.
 *
 * 펼쳐진 폴더 아래에는 같은 목록을 한 단계 깊게 다시 그리고,
 * 그 하위 목록 왼쪽에는 부모 폴더의 chevron 중앙을 지나는 들여쓰기 가이드를 세워요.
 */
export default function SidebarTreeList({
  nodes,
  depth,
  isFolderExpanded,
  onToggleFolder,
}: SidebarTreeListProps) {
  return (
    <List $depth={depth}>
      {nodes.map((node) => (
        <li key={node.id}>
          {node.type === "folder" ? (
            <>
              <SidebarFolderRow
                depth={depth}
                name={node.name}
                documentCount={node.documentCount}
                isExpanded={isFolderExpanded(node.id)}
                onToggle={() => onToggleFolder(node.id)}
              />
              {isFolderExpanded(node.id) && node.children.length > 0 && (
                <SidebarTreeList
                  nodes={node.children}
                  depth={depth + 1}
                  isFolderExpanded={isFolderExpanded}
                  onToggleFolder={onToggleFolder}
                />
              )}
            </>
          ) : (
            <SidebarFileRow depth={depth} name={node.name} />
          )}
        </li>
      ))}
    </List>
  );
}

const List = styled.ul<{ $depth: number }>`
  position: relative;

  /* 부모 폴더 행의 chevron 중앙(8px + 6px)에서 부모 깊이만큼 들여쓴 위치에 가이드를 세워요 */
  ${({ $depth, theme }) =>
    $depth > 0 &&
    css`
      &::before {
        content: "";
        position: absolute;
        top: 0.25rem; /* 4px */
        bottom: 0.25rem;
        left: calc(
          0.875rem + 1.125rem * ${$depth - 1}
        ); /* 14px + 18px × (depth − 1) */
        width: 1px;
        background-color: ${theme.neutral[300]};
      }
    `}
`;
