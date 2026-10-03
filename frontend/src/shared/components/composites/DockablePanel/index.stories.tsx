import styled from "@emotion/styled";
import type { Meta, StoryObj } from "@storybook/react-webpack5";
import { useState } from "react";

import ChatListIcon from "@/assets/icons/chatList.svg";
import SidebarIcon from "@/assets/icons/sidebar.svg";

import DockablePanel from ".";

const DOCK_RAIL_ID = "story-dock-rail";

/** 패널 안에 넣을 예시 내용. 실제로는 사이드바·대화 목록이 스스로 껍데기를 가져요. meta의 args가 바로 쓰므로 위에 둬요 */
const PanelContent = styled.ul`
  box-sizing: border-box;
  width: 17.5rem;
  height: 100%;
  margin: 0;
  padding: 1rem;
  border-radius: 1rem;
  background-color: ${({ theme }) => theme.neutral[0]};
  box-shadow: ${({ theme }) => theme.shadow02};
  color: ${({ theme }) => theme.neutral[800]};
  list-style: none;
`;

/**
 * 스치면 화면 위에 띄우고, 누르면 자리를 차지하는 패널이에요. 워크스페이스 GNB 왼쪽의 사이드바·대화 목록 버튼이 이 패널을 씁니다.
 *
 * **어떻게 뜨나**
 * - 트리거 버튼에 포인터를 얹거나 포커스를 주면 패널이 본문 위에 겹쳐 떠요(드롭다운). 잠깐 내용을 훑어볼 때예요.
 * - 버튼을 누르면 겹치는 대신 `dockTargetId` 자리(예: 화면 왼쪽 레일)로 옮겨 가 실제로 폭을 차지해요. 다시 누르면 접혀요.
 * - 어느 쪽으로 뜨든 왼쪽에서 밀려 들어오는 모션으로 나와요. 동작 줄이기 설정을 켠 사용자에게는 모션 없이 바로 나와요.
 *
 * **동작 규칙**
 * - 포인터가 트리거에서 패널로 건너가는 동안 잠깐 둘 다 벗어나므로, 벗어나자마자 닫지 않고 잠시 기다렸다 닫아요.
 * - 겹쳐 뜬 패널은 `Esc`를 누르거나 포커스가 패널 밖으로 나가면 닫혀요.
 * - 겹쳐 뜬 패널은 트리거마다 다른 자리가 아니라 늘 화면 왼쪽 위 같은 자리에 떠요. 디자인이 그렇게 잡혀 있어요.
 * - 고정된 동안에는 이미 자리를 차지하고 있으므로 스쳐도 따로 띄우지 않아요.
 *
 * **고정 여부는 누가 정하나**
 * - 기본은 패널이 스스로 여닫아요.
 * - 같은 자리를 여러 패널이 나눠 써서 하나만 고정돼야 하면, 그 자리를 아는 바깥이 `isDocked`·`onDockedChange`로 정해요.
 *   예: 사이드바를 고정한 채 대화 목록을 누르면 사이드바가 자리를 비워요.
 *
 * 어떤 내용을 담을지는 쓰는 쪽이 정하므로 이 컴포넌트는 도메인을 알지 못해요.
 * 패널의 껍데기(너비·배경·라운드)도 넘기는 내용이 스스로 가져요.
 *
 * **디자인 원본**: [GNB/Floating](https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=1364-6863) ·
 * [Sidebar/Drawer](https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=1382-2171) ·
 * [Btn/사이드바](https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=1364-836)
 */
const meta = {
  title: "Shared/DockablePanel",
  component: DockablePanel,
  args: {
    label: "사이드바",
    icon: <SidebarIcon size={18} />,
    dockTargetId: DOCK_RAIL_ID,
    children: (
      <PanelContent>
        <li>회의록</li>
        <li>기획 문서</li>
        <li>API 명세</li>
      </PanelContent>
    ),
  },
  argTypes: {
    icon: { control: false },
    children: { control: false },
  },
  parameters: {
    layout: "fullscreen",
    // 겹쳐 뜬 패널이 화면 기준으로 놓이므로 문서 페이지에서도 스토리마다 따로 그려요
    docs: { story: { inline: false, iframeHeight: 480 } },
  },
  decorators: [
    (Story) => (
      <Screen>
        <Gnb>
          <Story />
        </Gnb>
        <Body>
          <DockRail id={DOCK_RAIL_ID} />
          <Content>본문</Content>
        </Body>
      </Screen>
    ),
  ],
} satisfies Meta<typeof DockablePanel>;

export default meta;

type Story = StoryObj<typeof meta>;

/** 패널이 스스로 여닫는 기본 모양이에요. 버튼에 포인터를 얹으면 겹쳐 뜨고, 누르면 왼쪽에 자리를 잡아요. */
export const Default: Story = {};

/** 바깥이 고정을 정해 이미 자리를 차지한 상태예요. 예: 사용자가 사이드바를 고정해 둔 워크스페이스 화면 */
export const Docked: Story = {
  args: { isDocked: true },
};

/**
 * 두 패널이 왼쪽 자리 하나를 나눠 쓰는 모양이에요. 한 번에 하나만 고정되고, 다른 쪽을 누르면 먼저 있던 패널이 자리를 비워요.
 * 예: 탐색 화면의 사이드바와 대화 목록
 */
export const SharedDockTarget: Story = {
  render: () => <SharedDockTargetDemo />,
};

function SharedDockTargetDemo() {
  const [dockedPanel, setDockedPanel] = useState<"sidebar" | "chatList" | null>(
    null,
  );

  const pickPanel =
    (panel: "sidebar" | "chatList") => (isDocked: boolean) =>
      setDockedPanel(isDocked ? panel : null);

  return (
    <>
      <DockablePanel
        label="사이드바"
        icon={<SidebarIcon size={18} />}
        dockTargetId={DOCK_RAIL_ID}
        isDocked={dockedPanel === "sidebar"}
        onDockedChange={pickPanel("sidebar")}
      >
        <PanelContent>
          <li>회의록</li>
          <li>기획 문서</li>
          <li>API 명세</li>
        </PanelContent>
      </DockablePanel>
      <DockablePanel
        label="대화 목록"
        icon={<ChatListIcon size={18} />}
        dockTargetId={DOCK_RAIL_ID}
        isDocked={dockedPanel === "chatList"}
        onDockedChange={pickPanel("chatList")}
      >
        <PanelContent>
          <li>로그인 API 변경 이력</li>
          <li>지난주 회의 결정 사항</li>
        </PanelContent>
      </DockablePanel>
    </>
  );
}

const Screen = styled.div`
  display: flex;
  flex-direction: column;
  height: 30rem;
  background-color: ${({ theme }) => theme.neutral[50]};
`;

const Gnb = styled.div`
  display: flex;
  gap: 0.5rem;
  padding: 1.5rem 2.5rem 0;
`;

const Body = styled.div`
  display: flex;
  flex: 1;
  min-height: 0;
`;

/** 고정된 패널이 옮겨 오는 자리. 비어 있으면 폭을 차지하지 않아요 */
const DockRail = styled.div`
  display: flex;
  flex-shrink: 0;
  width: 0;
  padding: 1.25rem 0;

  &:has(> *) {
    width: 20rem;
    padding-left: 2.5rem;
  }
`;

const Content = styled.main`
  flex: 1;
  padding: 1.25rem 2.5rem;
  color: ${({ theme }) => theme.neutral[800]};
`;
