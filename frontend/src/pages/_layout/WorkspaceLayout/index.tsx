import DockablePanel from "@composites/DockablePanel";
import styled from "@emotion/styled";
import useWorkspaceEntry from "@hooks/domain/workspace/useWorkspaceEntry";
import useWorkspaceNav from "@hooks/domain/workspace/useWorkspaceNav";
import LoadingIndicator from "@primitives/ui/LoadingIndicator";
import ChatListDrawer from "@widgets/chat/ChatListDrawer";
import WorkspaceDock from "@widgets/workspace/WorkspaceDock";
import WorkspaceGnb from "@widgets/workspace/WorkspaceGnb";
import WorkspaceSidebar from "@widgets/workspace/WorkspaceSidebar";
import { Outlet, useParams } from "react-router";

import ChatListIcon from "@/assets/icons/chatList.svg";
import SidebarIcon from "@/assets/icons/sidebar.svg";

import { WORKSPACE_DOCK_RAIL_ID } from "./constants/dockRail";

/**
 * GNB 레이아웃
 *
 * 워크스페이스 입장 후의 내부 페이지(홈, 탐색)가 공유한다.
 * 위에 GNB, 아래 가운데에 독을 두고, 그 사이를 화면 콘텐츠가 채운다.
 *
 * GNB 좌측 버튼이 여는 패널(사이드바·대화 목록)은 스쳐 지나가면 본문 위에 겹쳐 뜨고,
 * 누르면 왼쪽 레일로 옮겨 가 실제로 폭을 차지한다. 그 판단은 `DockablePanel`이 하고
 * 이 레이아웃은 어떤 패널을 둘지와 옮겨 갈 자리만 정한다.
 * 대화 목록 버튼은 탐색 화면에서만 둔다.
 *
 * 들어갈 수 있는 워크스페이스인지도 이 레이아웃 범위에서 한 번만 판정한다(`useWorkspaceEntry`).
 * 워크스페이스 조회에 성공하기 전에는 본문 대신 스피너를 두고, 401은 로그인으로, 403·404는 선택 화면으로 보낸다.
 * 판정 규칙은 훅이 가지고 이 레이아웃은 배치만 맡는다.
 *
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=1364-6863 GNB/Floating
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=526-772 탐색 결과/채팅 세션 목록
 */
export default function WorkspaceLayout() {
  const { workspaceId } = useParams();
  const { isReady } = useWorkspaceEntry({ workspaceId: Number(workspaceId) });
  const { isChatActive } = useWorkspaceNav();

  return (
    <Container>
      <DockRail id={WORKSPACE_DOCK_RAIL_ID} />

      <Content>
        <GnbSlot>
          <WorkspaceGnb>
            <DockablePanel
              label="사이드바"
              icon={<SidebarIcon size={18} />}
              dockTargetId={WORKSPACE_DOCK_RAIL_ID}
            >
              <WorkspaceSidebar />
            </DockablePanel>

            {isChatActive && (
              <DockablePanel
                label="대화 목록"
                icon={<ChatListIcon size={18} />}
                dockTargetId={WORKSPACE_DOCK_RAIL_ID}
              >
                <ChatListDrawer />
              </DockablePanel>
            )}
          </WorkspaceGnb>
        </GnbSlot>

        <Main aria-busy={!isReady}>
          {isReady ? (
            <Outlet />
          ) : (
            <LoadingFallback label="워크스페이스를 불러오고 있어요" />
          )}
        </Main>

        <DockSlot>
          <WorkspaceDock />
        </DockSlot>
      </Content>
    </Container>
  );
}

const Container = styled.div`
  display: flex;
  height: 100%;
  background-color: ${({ theme }) => theme.neutral[50]};
`;

/**
 * 고정된 패널이 옮겨 오는 자리.
 *
 * 아무 패널도 고정돼 있지 않으면 폭을 차지하지 않아 본문이 화면을 다 쓴다.
 * 위아래 여백은 겹쳐 떴을 때(`DockablePanel`의 플로팅 위치)와 같은 자리에 놓이도록 맞췄다.
 */
const DockRail = styled.div`
  display: flex;
  flex-shrink: 0;
  width: 20rem; /* 320px = 왼쪽 여백 40px + 패널 280px */
  padding: 5.5rem 0 8.5rem 2.5rem; /* 88px 0 136px 40px */

  &:empty {
    display: none;
  }
`;

const Content = styled.div`
  position: relative;
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
`;

const GnbSlot = styled.div`
  flex-shrink: 0;
  padding-top: 1.5rem; /* 24px */
`;

const Main = styled.main`
  flex: 1;
  min-height: 0;
  padding-top: 1.25rem; /* 20px — GNB 아래 88px 지점에서 본문이 시작해요 */
  overflow-y: auto;
`;

/** 독은 본문 위에 떠 있지만 가로로 늘어난 자리가 본문 클릭을 가리지는 않아요 */
const DockSlot = styled.div`
  position: absolute;
  right: 0;
  bottom: 1.75rem; /* 28px */
  left: 0;
  display: flex;
  justify-content: center;
  pointer-events: none;

  & > * {
    pointer-events: auto;
  }
`;

/** 본문 자리를 그대로 채워 스피너가 화면 가운데에 놓이도록 해요. */
const LoadingFallback = styled(LoadingIndicator)`
  height: 100%;
  color: ${({ theme }) => theme.neutral[800]};
`;
