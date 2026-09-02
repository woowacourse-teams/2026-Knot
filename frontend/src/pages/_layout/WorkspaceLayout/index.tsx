import DockablePanel from "@composites/DockablePanel";
import styled from "@emotion/styled";
import useWorkspaceEntry from "@hooks/domain/workspace/useWorkspaceEntry";
import useWorkspaceNav from "@hooks/domain/workspace/useWorkspaceNav";
import LoadingIndicator from "@primitives/ui/LoadingIndicator";
import { ChatStreamProvider } from "@provider/context/chatStreamContext";
import ChatListDrawer from "@widgets/chat/ChatListDrawer";
import WorkspaceDock from "@widgets/workspace/WorkspaceDock";
import WorkspaceGnb from "@widgets/workspace/WorkspaceGnb";
import WorkspaceSidebar from "@widgets/workspace/WorkspaceSidebar";
import { useState } from "react";
import { Outlet, useParams } from "react-router";

import ChatListIcon from "@/assets/icons/chatList.svg";
import SidebarIcon from "@/assets/icons/sidebar.svg";

import { WORKSPACE_DOCK_RAIL_ID } from "./constants/dockRail";
import type { DockedPanelName } from "./types/dockedPanel";

/**
 * GNB 레이아웃
 *
 * 워크스페이스 입장 후의 내부 페이지(홈, 탐색)가 공유한다.
 * 위에 GNB, 아래 가운데에 독을 두고, 그 사이를 화면 콘텐츠가 채운다.
 *
 * GNB는 레일보다 위 칸에 있어 패널을 고정해도 화면 전체 폭을 그대로 쓴다.
 * 레일과 본문은 GNB 아래 남은 높이(`100vh - GNB 높이`)를 나눠 쓴다.
 *
 * GNB 좌측 버튼이 여는 패널(사이드바·대화 목록)은 스쳐 지나가면 본문 위에 겹쳐 뜨고,
 * 누르면 왼쪽 레일로 옮겨 가 실제로 폭을 차지한다. 뜨고 지는 방식은 `DockablePanel`이 정하고
 * 이 레이아웃은 어떤 패널을 둘지와 옮겨 갈 자리, 그리고 그중 무엇이 고정될지를 정한다.
 * 레일은 하나만 담으므로 고정은 한 번에 한 패널이고, 다른 패널을 누르면 먼저 있던 것이 자리를 비운다.
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
  const [pickedPanel, setPickedPanel] = useState<DockedPanelName>(null);

  // 대화 목록 버튼은 탐색 화면에만 있으므로, 홈으로 나가면 고른 적 없던 것으로 봐요
  const dockedPanel =
    !isChatActive && pickedPanel === "chatList" ? null : pickedPanel;

  const pickPanel =
    (panel: Exclude<DockedPanelName, null>) => (isDocked: boolean) =>
      setPickedPanel(isDocked ? panel : null);

  return (
    <ChatStreamProvider>
      <Container>
        <GnbSlot>
          <WorkspaceGnb>
            <DockablePanel
              label="사이드바"
              icon={<SidebarIcon size={18} />}
              dockTargetId={WORKSPACE_DOCK_RAIL_ID}
              isDocked={dockedPanel === "sidebar"}
              onDockedChange={pickPanel("sidebar")}
            >
              <WorkspaceSidebar />
            </DockablePanel>

            {isChatActive && (
              <DockablePanel
                label="대화 목록"
                icon={<ChatListIcon size={18} />}
                dockTargetId={WORKSPACE_DOCK_RAIL_ID}
                isDocked={dockedPanel === "chatList"}
                onDockedChange={pickPanel("chatList")}
              >
                <ChatListDrawer />
              </DockablePanel>
            )}
          </WorkspaceGnb>
        </GnbSlot>

        <Body>
          <DockRail id={WORKSPACE_DOCK_RAIL_ID} />

          <Content>
            <Main aria-busy={!isReady}>
              {isReady ? (
                <Outlet />
              ) : (
                <LoadingFallback label="워크스페이스를 불러오고 있어요" />
              )}
            </Main>
          </Content>
        </Body>

        <DockSlot>
          <WorkspaceDock />
        </DockSlot>
      </Container>
    </ChatStreamProvider>
  );
}

const Container = styled.div`
  position: relative; /* 독이 레일·본문과 무관하게 화면을 기준으로 놓여요 */
  display: flex;
  flex-direction: column;
  height: 100%;
  background-color: ${({ theme }) => theme.neutral[50]};
`;

/**
 * GNB 자리.
 *
 * 레일·본문보다 위 칸에 있어 늘 화면 전체 폭을 쓴다.
 * 패널을 고정해도 GNB는 줄어들지 않고, 가운데 내비 필도 화면 한가운데에 그대로 있는다.
 */
const GnbSlot = styled.div`
  flex-shrink: 0;
  padding-top: 1.5rem; /* 24px */
`;

/** GNB 아래 남은 높이를 레일과 본문이 나눠 쓰는 칸 */
const Body = styled.div`
  display: flex;
  flex: 1;
  min-height: 0;
`;

/**
 * 고정된 패널이 옮겨 오는 자리.
 *
 * 아무 패널도 고정돼 있지 않으면 폭을 차지하지 않아 본문이 화면을 다 쓴다.
 * GNB 아래 칸에 있으므로 화면 전체가 아니라 `100vh - GNB 높이`만큼만 차지한다.
 * 위아래 여백은 겹쳐 떴을 때(`DockablePanel`의 플로팅 위치)와 같은 자리에 놓이도록 맞췄다.
 *
 * 패널이 들어오고 나갈 때 폭이 0에서 320px 사이를 오가며 본문을 부드럽게 밀어낸다.
 * 패널은 포털로 들어오므로 이 자리가 찼는지는 `:has`로 본다.
 * 시간과 감속은 패널의 등장 모션(`DockablePanel`)과 맞춰 함께 밀리는 것처럼 보이게 했다.
 */
const DockRail = styled.div`
  display: flex;
  flex-shrink: 0;
  width: 0;
  padding: 1.25rem 0 8.5rem; /* 20px 0 136px — GNB 아래 88px 지점에서 시작해요 */
  transition:
    width 0.28s cubic-bezier(0.22, 1, 0.36, 1),
    padding-left 0.28s cubic-bezier(0.22, 1, 0.36, 1);

  &:has(> *) {
    width: 20rem; /* 320px = 왼쪽 여백 40px + 패널 280px */
    padding-left: 2.5rem; /* 40px */
  }

  @media (prefers-reduced-motion: reduce) {
    transition: none;
  }
`;

const Content = styled.div`
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
`;

const Main = styled.main`
  flex: 1;
  min-height: 0;
  padding-top: 1.25rem; /* 20px — GNB 아래 88px 지점에서 본문이 시작해요 */
  overflow-y: auto;
`;

/**
 * 독이 놓이는 자리.
 *
 * 레일이 아니라 화면 전체를 기준으로 잡아, 사이드바나 대화 목록이 열려도 독은 늘 화면 한가운데에 있어요.
 * 가로로 늘어난 자리가 본문 클릭을 가리지는 않아요.
 */
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
