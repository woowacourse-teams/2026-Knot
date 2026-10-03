import { PATH_ROUTE, getRouterPath } from "@routes/PATH_ROUTE";
import { useRecordingStore } from "@store/recordingStore";
import type { Meta, StoryObj } from "@storybook/react-webpack5";
import { MemoryRouter, Route, Routes } from "react-router";

import RecorderBar from ".";

const RECORDING_PATH = getRouterPath({
  routeKey: "RECORDING",
  params: { workspaceId: "1" },
});

/** 녹음이 없는 처음 상태로 되돌려요. 녹음은 전역 저장소에 있어 스토리끼리 이어지기 때문이에요. */
const resetRecording = () => {
  useRecordingStore.setState({
    status: "idle",
    accumulatedMs: 0,
    resumedAt: null,
  });
};

/**
 * 녹음 화면 맨 위에 놓이는 녹음 조작 바예요.
 *
 * 왼쪽에 녹음 상태·녹음한 시간·파형을, 오른쪽에 「일시정지」(일시정지 중이면 「이어서 녹음」)와 「녹음 끝내기」 버튼을 둬요.
 *
 * **동작 규칙**
 * - 녹음 화면에 들어오면 바로 녹음을 시작해요. 다른 화면에 다녀와 다시 들어오면 새로 시작하지 않고 이어지던 녹음을 그대로 보여 줘요. 녹음 상태가 전역에 있어 화면을 떠나도 끊기지 않습니다.
 * - 「녹음 끝내기」를 누르면 녹음을 처음 상태로 되돌리고 워크스페이스 홈으로 나가요. 끝난 녹음이 남은 화면에 머물면 새 녹음이 시작된 것처럼 헷갈리기 때문이에요.
 * - 녹음을 시작한 사람 혼자 쓰는 화면이라 권한에 따른 구분은 없어요.
 * - 시간이 한 시간을 넘으면 `분:초`에서 `시:분:초`로 바뀌어요. 시간이 길어져도 상태 글자 칸은 폭이 고정이라 옆 글자가 밀리지 않아요.
 * - 아직 마이크·업로드를 연결하지 않은 UI 단계라 시간과 상태만 바뀌고, 파형은 정해 둔 모양을 그리는 장식이에요. 장식이라 화면 낭독기에서는 읽지 않아요.
 * - 바의 폭이 좁아지면 파형이 끝부터 잘려요.
 */
const meta = {
  title: "Recording/RecorderBar",
  component: RecorderBar,
  parameters: {
    layout: "padded",
    design: {
      type: "figma",
      url: "https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=1705-3280",
    },
  },
  beforeEach: resetRecording,
  decorators: [
    (Story) => (
      <MemoryRouter initialEntries={[RECORDING_PATH]}>
        <Routes>
          <Route path={PATH_ROUTE.RECORDING} element={<Story />} />
          <Route
            path="*"
            element={<p>녹음을 끝내고 워크스페이스 홈으로 이동했어요.</p>}
          />
        </Routes>
      </MemoryRouter>
    ),
  ],
} satisfies Meta<typeof RecorderBar>;

export default meta;

type Story = StoryObj<typeof meta>;

/** 녹음 화면에 막 들어와 녹음이 시작된 상태예요. 시간이 1초마다 늘어나고, 파형은 끝의 몇 개 막대만 흐려요. */
export const Recording: Story = {};

/**
 * 녹음을 잠시 멈춘 상태예요. 상태 글자와 점이 회색이 되고, 파형이 모두 흐려지며, 버튼이 「이어서 녹음」으로 바뀌어요.
 * 예: 회의 중 쉬는 시간
 */
export const Paused: Story = {
  beforeEach: () => {
    useRecordingStore.setState({
      status: "paused",
      accumulatedMs: (12 * 60 + 48) * 1000,
      resumedAt: null,
    });
  },
};

/** 한 시간을 넘긴 녹음이에요. 시간이 `시:분:초`로 바뀌어도 상태 글자는 제자리에 있어요. 예: 긴 정기 회의 */
export const OverAnHour: Story = {
  beforeEach: () => {
    useRecordingStore.setState({
      status: "recording",
      accumulatedMs: (60 * 60 + 2 * 60 + 3) * 1000,
      resumedAt: Date.now(),
    });
  },
};
