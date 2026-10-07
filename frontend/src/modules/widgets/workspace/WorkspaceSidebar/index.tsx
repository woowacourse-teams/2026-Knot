import useNotionPageTreeQuery from "@api/queries/useNotionPageTreeQuery";
import styled from "@emotion/styled";
import WorkspaceSwitcher from "@features/workspace/WorkspaceSwitcher";
import { useParams } from "react-router";

import { useWorkspaceTree } from "./model/useWorkspaceTree";
import SidebarTreeList from "./ui/SidebarTreeList";
import { toWorkspaceTree } from "./utils/toWorkspaceTree";

/**
 * 워크스페이스 사이드바 드로어. 워크스페이스 이름과 Notion 페이지 트리를 보여줘요.
 *
 * 맨 위의 워크스페이스 이름(`WorkspaceSwitcher`)을 누르면 다른 워크스페이스로 옮겨 가거나 새로 만들 수 있어요.
 */
export default function WorkspaceSidebar() {
  const { workspaceId } = useParams();
  const { isFolderExpanded, toggleFolder } = useWorkspaceTree();
  const { data: pageTree } = useNotionPageTreeQuery({
    workspaceId: Number(workspaceId),
  });

  // 서버는 부모 ID만 달린 평평한 목록을 주므로 트리 모양으로 묶는 일은 여기서 해요.
  // 페이지 수가 많지 않고 응답 DTO는 refetch마다 참조가 바뀌므로 메모이제이션하지 않아요
  const treeNodes = toWorkspaceTree(pageTree?.pages ?? []);

  return (
    <Container aria-label="워크스페이스 사이드바">
      <WorkspaceSwitcher />

      <FolderHead>
        <FolderLabel>폴더</FolderLabel>
      </FolderHead>

      <SidebarTreeList
        nodes={treeNodes}
        depth={0}
        isFolderExpanded={isFolderExpanded}
        onToggleFolder={toggleFolder}
      />
    </Container>
  );
}

const Container = styled.aside`
  display: flex;
  flex-direction: column;
  gap: 0.75rem; /* 12px */
  width: 17.5rem; /* 280px */
  height: 100%;
  padding: 1rem; /* 16px */
  border: 1px solid ${({ theme }) => theme.neutral[200]};
  border-radius: 1.5rem; /* 24px */
  background-color: ${({ theme }) => theme.neutral[0]};
  box-shadow: ${({ theme }) => theme.shadow03};
  overflow-y: auto;
`;

const FolderHead = styled.div`
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: space-between;
  padding: 0 0.5rem 0 0.625rem; /* 0 8px 0 10px */
  color: ${({ theme }) => theme.neutral[400]};
`;

const FolderLabel = styled.span`
  ${({ theme }) => theme.text.caption01};
`;
