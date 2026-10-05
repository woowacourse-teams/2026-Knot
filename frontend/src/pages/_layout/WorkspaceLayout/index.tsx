import DockablePanel from "@composites/DockablePanel";
import styled from "@emotion/styled";
import useRecordingLeaveWarning from "@hooks/domain/recording/useRecordingLeaveWarning";
import useWorkspaceEntry from "@hooks/domain/workspace/useWorkspaceEntry";
import useWorkspaceNav from "@hooks/domain/workspace/useWorkspaceNav";
import DockColumn from "@primitives/layout/DockColumn";
import LoadingIndicator from "@primitives/ui/LoadingIndicator";
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
 * 위에 GNB를 두고, 그 아래를 왼쪽 레일과 본문이 나눠 쓴다.
 *
 * GNB 좌측 버튼이 여는 패널(사이드바·대화 목록)은 스쳐 지나가면 본문 위에 겹쳐 뜨고,
 * 누르면 왼쪽 레일로 옮겨 가 폭을 차지한다. 레일은 한 번에 한 패널만 담는다.
 * 대화 목록 버튼은 탐색 화면에서만 둔다.
 *
 * 독과 탐색 대화 열은 둘 다 본문 영역 안의 `DockColumn`에 놓여, 패널을 고정하면 함께 밀린다(SEARCH-R11).
 * 오른쪽 패널도 이 레이아웃의 레일로 올리면 본문 영역이 줄어 같은 규칙을 따른다.
 *
 * 들어갈 수 있는 워크스페이스인지도 이 레이아웃 범위에서 한 번만 판정한다(`useWorkspaceEntry`).
 * 조회에 성공하기 전에는 본문 대신 스피너를 두고, 401은 로그인으로, 403·404는 선택 화면으로 보낸다.
 *
 * 녹음은 레이아웃 안의 어느 화면에서나 이어지므로, 녹음 중 새로고침·탭 닫기 경고도 이 범위에서 건다.
 *
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=1364-6863 GNB/Floating
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=526-772 탐색 결과/채팅 세션 목록
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=2106-28974 탐색/대화 목록 고정 + 찾은 기록 열림
 */
export default function WorkspaceLayout() {
  const { workspaceId } = useParams();
  const { isReady } = useWorkspaceEntry({ workspaceId: Number(workspaceId) });
  const { isChatActive } = useWorkspaceNav();
  const [pickedPanel, setPickedPanel] = useState<DockedPanelName>(null);

  useRecordingLeaveWarning();

  // 대화 목록 버튼은 탐색 화면에만 있으므로, 홈으로 나가면 고른 적 없던 것으로 봐요
  const dockedPanel =
    !isChatActive && pickedPanel === "chatList" ? null : pickedPanel;

  const pickPanel =
    (panel: Exclude<DockedPanelName, null>) => (isDocked: boolean) =>
      setPickedPanel(isDocked ? panel : null);

  return (
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

          <DockSlot>
            <WorkspaceDock />
          </DockSlot>
        </Content>
      </Body>
    </Container>
  );
}

const Container = styled.div`
  display: flex;
  flex-direction: column;
  height: 100%;
  background-color: ${({ theme }) => theme.neutral[50]};
`;

/** 레일·본문보다 위 칸이라, 패널을 고정해도 화면 전체 폭을 그대로 쓴다. */
const GnbSlot = styled.div`
  flex-shrink: 0;
  padding-top: 1.5rem; /* 24px */
`;

const Body = styled.div`
  display: flex;
  flex: 1;
  min-height: 0;
`;

/**
 * 고정된 패널이 옮겨 오는 자리. 비어 있으면 폭이 0이다.
 *
 * 패널은 포털로 들어오므로 찼는지는 `:has`로 보고,
 * 폭 전환은 패널 등장 모션(`DockablePanel`)과 같은 시간·감속으로 맞춰 함께 밀리는 것처럼 보이게 한다.
 */
const DockRail = styled.div`
  display: flex;
  flex-shrink: 0;
  width: 0;
  padding: 1.25rem 0 8.5rem; /* 20px 0 136px */
  transition:
    width 0.28s cubic-bezier(0.22, 1, 0.36, 1),
    padding-left 0.28s cubic-bezier(0.22, 1, 0.36, 1);

  &:has(> *) {
    width: 20rem; /* 320px */
    padding-left: 2.5rem; /* 40px */
  }

  @media (prefers-reduced-motion: reduce) {
    transition: none;
  }
`;

/** 고정 패널을 뺀 남은 영역. 독과 대화 열이 이 폭을 기준으로 놓인다. */
const Content = styled.div`
  position: relative;
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
`;

const Main = styled.main`
  flex: 1;
  min-height: 0;
  padding-top: 1.25rem; /* 20px */
  overflow-y: auto;
`;

/** 독이 놓이는 자리. 본문 위에 떠 있지만 독 바깥은 본문 클릭을 가리지 않는다. */
const DockSlot = styled(DockColumn)`
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
