import useNotionPageTreeQuery from "@api/queries/useNotionPageTreeQuery";
import useWorkspaceQuery from "@api/queries/useWorkspaceQuery";
import styled from "@emotion/styled";
import Avatar from "@primitives/ui/Avatar";

import { useParams } from "react-router";

import { useWorkspaceTree } from "./model/useWorkspaceTree";
import SidebarTreeList from "./ui/SidebarTreeList";
import { toWorkspaceTree } from "./utils/toWorkspaceTree";

/**
 * 워크스페이스 이름과 Notion 페이지 트리를 보여주는 사이드바 드로어.
 *
 * 동작 규칙은 스토리북 `Workspace/WorkspaceSidebar`에서 확인해요.
 */
export default function WorkspaceSidebar() {
  const { workspaceId } = useParams();
  const { isFolderExpanded, toggleFolder } = useWorkspaceTree();
  // 레이아웃의 진입 판정과 같은 쿼리라 요청은 한 번만 나가요
  const { data: workspace } = useWorkspaceQuery({
    workspaceId: Number(workspaceId),
  });
  const { data: pageTree } = useNotionPageTreeQuery({
    workspaceId: Number(workspaceId),
  });

  const workspaceName = workspace?.name ?? "";
  // 서버는 부모 ID만 달린 평평한 목록을 주므로 트리 모양으로 묶는 일은 여기서 해요.
  // 페이지 수가 많지 않고 응답 DTO는 refetch마다 참조가 바뀌므로 메모이제이션하지 않아요
  const treeNodes = toWorkspaceTree(pageTree?.pages ?? []);

  return (
    <Container aria-label="워크스페이스 사이드바">
      <WorkspaceHeader>
        <WorkspaceInfo>
          <Avatar
            label={workspaceName || "워크스페이스"}
            name={workspaceName}
            size={24}
          />
          <WorkspaceName>{workspaceName}</WorkspaceName>
        </WorkspaceInfo>
      </WorkspaceHeader>

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

const WorkspaceHeader = styled.div`
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: space-between;
  height: 2.5rem; /* 40px */
  padding: 0 0.5rem; /* 8px */
  color: ${({ theme }) => theme.neutral[400]};
`;

const WorkspaceInfo = styled.div`
  display: flex;
  align-items: center;
  gap: 0.625rem; /* 10px */
  min-width: 0;
`;

const WorkspaceName = styled.span`
  overflow: hidden;
  color: ${({ theme }) => theme.neutral[900]};
  white-space: nowrap;
  text-overflow: ellipsis;
  ${({ theme }) => theme.text.label01};
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
