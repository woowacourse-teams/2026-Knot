import type { Meta, StoryObj } from "@storybook/react-webpack5";
import { fn } from "storybook/test";

import RecordingListCard from ".";

/**
 * 워크스페이스 홈의 「진행 중인 녹음」 카드예요. 내가 지금 진행 중인 녹음 하나만 보여 줘요.
 *
 * **동작 규칙**
 * - 다른 사람의 녹음은 보이지 않고, 개수도 표시하지 않아요. 함께 녹음·여러 녹음은 이후 버전이에요.
 * - 녹음이 있으면 한 칸에 상태(점 + 상태 · 녹음한 시간), 녹음 이름, 안내 한 줄과 「녹음 화면으로」 버튼을 보여 줘요.
 * - 녹음 중이면 빨간 점·빨간 글자, 일시정지면 회색 점·회색 글자예요.
 * - 녹음이 없으면 빈 상태 문구로 독의 마이크에서 녹음을 시작하도록 안내해요.
 * - 녹음을 조회하는 동안에는 칸 자리를 뼈대로 채워 카드 높이가 흔들리지 않게 해요.
 * - 녹음 화면으로 갈 수 없는 곳에서는 「녹음 화면으로」 버튼을 숨겨요. 예: 녹음을 시작한 탭이 아닌 다른 탭의 홈
 * - 문서 정리 중·문서 정리 실패 상태는 아직 다루지 않아요.
 */
const meta = {
  title: "Recording/RecordingListCard",
  component: RecordingListCard,
  parameters: {
    layout: "centered",
    design: {
      type: "figma",
      url: "https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=1783-13368",
    },
  },
  args: {
    recording: null,
    isLoading: false,
    onOpenRecording: fn(),
  },
} satisfies Meta<typeof RecordingListCard>;

export default meta;

type Story = StoryObj<typeof meta>;

/** 진행 중인 녹음이 없을 때예요. 홈에 처음 들어왔거나 녹음을 끝낸 뒤의 모습이에요. */
export const Empty: Story = {
  parameters: {
    design: {
      type: "figma",
      url: "https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=1783-13367",
    },
  },
};

/** 녹음이 이어지고 있을 때예요. 독의 마이크로 녹음을 시작하고 홈으로 돌아온 경우예요. */
export const Recording: Story = {
  args: {
    recording: {
      status: "recording",
      title: "유월 님의 녹음",
      elapsedTime: "12:48",
    },
  },
  parameters: {
    design: {
      type: "figma",
      url: "https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=1783-13269",
    },
  },
};

/** 녹음을 잠시 멈췄을 때예요. 녹음 화면에서 일시정지하고 홈으로 돌아온 경우예요. */
export const Paused: Story = {
  args: {
    recording: {
      status: "paused",
      title: "유월 님의 녹음",
      elapsedTime: "12:48",
    },
  },
  parameters: {
    design: {
      type: "figma",
      url: "https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=2377-35853",
    },
  },
};

/** 녹음 화면으로 갈 수 없는 곳에서 볼 때예요. 예: 녹음을 시작한 탭이 아닌 다른 탭의 홈 */
export const WithoutOpenButton: Story = {
  args: {
    ...Recording.args,
    onOpenRecording: undefined,
  },
};

/** 녹음을 조회하는 중이에요. 홈에 들어오자마자 잠깐 보여요. */
export const Loading: Story = {
  args: { isLoading: true },
};
