import useWorkspaceQuery from "@api/queries/useWorkspaceQuery";
import styled from "@emotion/styled";
import Avatar from "@primitives/ui/Avatar";

import { useParams } from "react-router";

import { WORKSPACE_TREE } from "./constants/workspaceSidebar";
import { useWorkspaceTree } from "./model/useWorkspaceTree";
import SidebarTreeList from "./ui/SidebarTreeList";

/**
 * 워크스페이스 사이드바 드로어.
 *
 * GNB 좌측의 사이드바 버튼이 여닫아요. 스쳐 지나가면 본문 위에 겹쳐 뜨고, 누르면 왼쪽에 자리를 잡아요.
 * 뜨는 방식과 자리는 이 위젯이 아니라 감싸는 `DockablePanel`이 정하고, 여기서는 드로어 껍데기와 내용만 그려요.
 *
 * 헤더의 워크스페이스 이름은 현재 `:workspaceId`의 워크스페이스 조회 응답에서 오고, 레이아웃의 진입 판정과
 * 같은 쿼리라 요청은 한 번만 나가요. 응답 전에는 이름 자리를 비워 둬요.
 * 임시 트리의 폴더 행만 눌러서 펼치고 접을 수 있어요.
 *
 * @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=1382-2171 Sidebar/Drawer}
 */
export default function WorkspaceSidebar() {
  const { workspaceId } = useParams();
  const { isFolderExpanded, toggleFolder } = useWorkspaceTree();
  const { data: workspace } = useWorkspaceQuery({
    workspaceId: Number(workspaceId),
  });

  const workspaceName = workspace?.name ?? "";

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
        nodes={WORKSPACE_TREE}
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

/** @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=580-1442 Sidebar/Workspace} */
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

/** @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=1381-5285 Sidebar/FolderHead} */
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
