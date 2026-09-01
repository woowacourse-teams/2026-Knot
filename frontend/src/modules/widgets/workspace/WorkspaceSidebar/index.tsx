import useWorkspaceQuery from "@api/queries/useWorkspaceQuery";
import styled from "@emotion/styled";
import Spacing from "@primitives/layout/Spacing";
import {
  useWorkspaceSidebar,
  WORKSPACE_SIDEBAR_ID,
} from "@provider/context/workspaceSidebarContext";

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
 * 워크스페이스 사이드바.
 *
 * GNB의 토글 버튼으로 열리며, 열리면 264px 너비로 왼쪽에 자리 잡고 GNB·본문을 오른쪽으로 밀어요.
 * 헤더의 워크스페이스 이름은 현재 `:workspaceId`의 워크스페이스 조회 응답에서 오고, 레이아웃의 진입 판정과
 * 같은 쿼리라 요청은 한 번만 나가요. 응답 전에는 이름 자리를 비워 둬요.
 * 워크스페이스 전환·폴더 추가·하단 동기화는 API가 없어 모양만 그리고,
 * 임시 트리의 폴더 행만 눌러서 펼치고 접을 수 있어요.
 *
 * 닫혀 있을 때는 아무것도 그리지 않지만 컴포넌트는 남아 있어 트리 펼침 상태가 유지돼요.
 *
 * @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=600-10208 Sidebar}
 * @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=600-10207 탐색 결과/사이드바 오픈}
 */
export default function WorkspaceSidebar() {
  const { workspaceId } = useParams();
  const { isSidebarOpen } = useWorkspaceSidebar();
  const { isFolderExpanded, toggleFolder } = useWorkspaceTree();
  const { data: workspace } = useWorkspaceQuery({
    workspaceId: Number(workspaceId),
  });

  if (!isSidebarOpen) return null;

  const workspaceName = workspace?.name ?? "";

  return (
    <Container id={WORKSPACE_SIDEBAR_ID} aria-label="워크스페이스 사이드바">
      <WorkspaceHeader>
        <WorkspaceInfo>
          <InitialAvatar aria-hidden>{workspaceName.charAt(0)}</InitialAvatar>
          <WorkspaceName>{workspaceName}</WorkspaceName>
        </WorkspaceInfo>
        <ChevronDownIcon size={12} />
      </WorkspaceHeader>
      <Spacing size={1.25} /> {/* 20px */}
      <FolderHead>
        <FolderLabel>폴더</FolderLabel>
        <PlusIcon size={14} />
      </FolderHead>
      <Spacing size={0.625} /> {/* 10px */}
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
  flex-shrink: 0;
  width: 16.5rem; /* 264px */
  height: 100%;
  padding: 1rem 0.75rem; /* 16px 12px */
  border-right: 1px solid ${({ theme }) => theme.neutral[200]};
  background-color: ${({ theme }) => theme.neutral[100]};
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

/** @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=587-516 Avatar size=24 type=이니셜} */
const InitialAvatar = styled.span`
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 1.5rem; /* 24px */
  height: 1.5rem;
  border-radius: 50%;
  background-color: ${({ theme }) => theme.neutral[800]};
  color: ${({ theme }) => theme.neutral[0]};
  ${({ theme }) => theme.text.caption01};
`;

const WorkspaceName = styled.span`
  overflow: hidden;
  color: ${({ theme }) => theme.neutral[900]};
  white-space: nowrap;
  text-overflow: ellipsis;
  ${({ theme }) => theme.text.label01};
`;

/** @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=580-1443 Sidebar/FolderHead} */
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

/** @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=580-1446 Sidebar/SyncPill} */
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
