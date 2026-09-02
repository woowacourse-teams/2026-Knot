import useWorkspaceQuery from "@api/queries/useWorkspaceQuery";
import styled from "@emotion/styled";
import Avatar from "@primitives/ui/Avatar";

import ChevronDownIcon from "@/assets/icons/chevronDown.svg";
import PlusIcon from "@/assets/icons/plus.svg";
import SyncIcon from "@/assets/icons/sync.svg";
import { useParams } from "react-router";

import {
  LAST_SYNCED_LABEL,
  WORKSPACE_TREE,
} from "./constants/workspaceSidebar";
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
 * 워크스페이스 전환·폴더 추가·하단 동기화는 API가 없어 모양만 그리고,
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
        <ChevronDownIcon size={12} />
      </WorkspaceHeader>

      <FolderHead>
        <FolderLabel>폴더</FolderLabel>
        <PlusIcon size={14} />
      </FolderHead>

      <SidebarTreeList
        nodes={WORKSPACE_TREE}
        depth={0}
        isFolderExpanded={isFolderExpanded}
        onToggleFolder={toggleFolder}
      />

      <SyncPill>
        <SyncStatus>
          <SyncIcon size={16} />
          지금 동기화
        </SyncStatus>
        <SyncedAt>
          <SyncedDot />
          {LAST_SYNCED_LABEL}
        </SyncedAt>
      </SyncPill>
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

/** @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=1381-5292 Sidebar/SyncPill} */
const SyncPill = styled.div`
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
  height: 2.75rem; /* 44px */
  margin-top: auto;
  padding: 0 0.75rem 0 0.875rem; /* 0 12px 0 14px */
  border: 1px solid ${({ theme }) => theme.neutral[200]};
  border-radius: 62.4375rem; /* 999px */
  background-color: ${({ theme }) => theme.neutral[0]};
`;

const SyncStatus = styled.span`
  display: flex;
  align-items: center;
  gap: 0.5rem; /* 8px */
  color: ${({ theme }) => theme.neutral[800]};
  ${({ theme }) => theme.text.caption02};

  & > svg {
    flex-shrink: 0;
    color: ${({ theme }) => theme.neutral[400]};
  }
`;

const SyncedAt = styled.span`
  display: flex;
  align-items: center;
  gap: 0.25rem; /* 4px */
  color: ${({ theme }) => theme.neutral[400]};
  ${({ theme }) => theme.text.caption01};
`;

/** @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=687-558 Indicator/Dot 없음} */
const SyncedDot = styled.span`
  display: block;
  width: 0.75rem; /* 12px 상자 안 6px 점 */
  height: 0.75rem;
  border: 0.1875rem solid transparent; /* 3px */
  border-radius: 50%;
  background-color: ${({ theme }) => theme.sub.accent[500]};
  background-clip: padding-box;
`;
